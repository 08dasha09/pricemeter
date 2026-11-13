package org.viewer.pricemeter.matching.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.matching.MatchingConfig;
import org.viewer.pricemeter.matching.MatchingService;
import org.viewer.pricemeter.matching.cluster.OfferClusterer;
import org.viewer.pricemeter.matching.normalize.BrandExtractor;
import org.viewer.pricemeter.matching.normalize.ModelCodeExtractor;
import org.viewer.pricemeter.matching.normalize.TitleNormalizer;
import org.viewer.pricemeter.matching.similarity.MatchCandidateFactory;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.matching.similarity.SimilarityScorer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogService.class, MatchingService.class, MatchingConfig.class,
        MatchingEvaluator.class, PairScanner.class, MatchCandidateFactory.class, OfferClusterer.class,
        SimilarityScorer.class, TitleNormalizer.class, BrandExtractor.class, ModelCodeExtractor.class})
class MatchingEvaluatorTest {

    private static final String FRIDGES = "REFRIGERATORS";
    private static final Instant T0 = Instant.parse("2026-10-10T10:00:00Z");

    @Autowired
    private MatchingEvaluator evaluator;

    @Autowired
    private MatchingService matching;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private MatchLabelRepository labels;

    @Test
    void comparesCurrentDecisionsWithLabels() {
        long citrus = offer("CITRUS", "C-1", "Холодильник Gorenje RK4182PS4", "Gorenje");
        long epicentr = offer("EPICENTR", "E-1", "Холодильник Gorenje RK4182PS4", "Gorenje");
        long citrusOther = offer("CITRUS", "C-2", "Холодильник Gorenje NRK6192AW4", "Gorenje");
        long eldorado = offer("ELDORADO", "D-2", "Холодильник Gorenje NRK6192AXL4", "Gorenje");
        long moyo = offer("MOYO", "M-3", "Холодильник Bosch KGN39VLEB", "Bosch");
        long epicentrBosch = offer("EPICENTR", "E-3", "Холодильник Bosch KGN39VLEB", "Bosch");
        matching.matchCategory(FRIDGES);

        label(citrus, epicentr, true);          // об'єднано правильно
        label(citrusOther, eldorado, true);     // пропущений збіг: літери коду відрізняються
        label(moyo, epicentrBosch, false);      // хибне об'єднання

        MatchingEvaluation evaluation = evaluator.evaluate();

        assertThat(evaluation.labeled()).isEqualTo(3);
        assertThat(evaluation.same()).isEqualTo(2);
        assertThat(evaluation.skipped()).isZero();
        assertThat(evaluation.system().sample()).isEqualTo(new Metrics(1, 1, 1, 0));
        assertThat(evaluation.system().sample().precision()).isEqualTo(0.5);
        assertThat(evaluation.system().sample().recall()).isEqualTo(0.5);
        // обидві пари з оцінкою 1 розмічені — вага 1
        assertThat(evaluation.system().weighted().precision()).isEqualTo(0.5);

        // Gorenje: 4 пропозиції з трьох магазинів → 5 пар різних магазинів; Bosch — 1 пара; між брендами — жодної
        assertThat(evaluation.population().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(6);
        assertThat(evaluation.population().get(ScoreBand.AUTO_HIGH)).isEqualTo(2);

        assertThat(evaluation.curve()).hasSize(20);
        MatchingEvaluation.ThresholdPoint lowest = evaluation.curve().getFirst();
        assertThat(lowest.threshold()).isEqualTo(0.05);
        assertThat(lowest.estimate().sample()).isEqualTo(new Metrics(2, 1, 0, 0));
        assertThat(evaluation.curve().getLast().threshold()).isEqualTo(1.0);
        assertThat(evaluation.curve().getLast().estimate().sample().fn()).isEqualTo(1);

        assertThat(evaluation.errors()).hasSize(2);
        MatchingEvaluation.PairOutcome falsePositive = evaluation.errors().getFirst();
        assertThat(falsePositive.systemSame()).isTrue();
        assertThat(falsePositive.labeledSame()).isFalse();
        assertThat(falsePositive.a().brand()).isEqualTo("BOSCH");
        MatchingEvaluation.PairOutcome falseNegative = evaluation.errors().getLast();
        assertThat(falseNegative.labeledSame()).isTrue();
        assertThat(falseNegative.score().reason()).isEqualTo(MatchScore.Reason.SIMILAR_CODE);

        assertThat(evaluation.autoThreshold()).isEqualTo(0.9);
        assertThat(evaluation.reviewThreshold()).isEqualTo(0.65);
        // хибне об'єднання з оцінкою 1 — жоден поріг не дає потрібної точності
        assertThat(evaluation.suggestedAuto()).isNull();
    }

    @Test
    void evaluatesNothingWithoutLabels() {
        offer("CITRUS", "C-1", "Холодильник Gorenje RK4182PS4", "Gorenje");
        offer("EPICENTR", "E-1", "Холодильник Gorenje RK4182PS4", "Gorenje");

        MatchingEvaluation evaluation = evaluator.evaluate();

        assertThat(evaluation.labeled()).isZero();
        assertThat(evaluation.system().sample().precision()).isNull();
        assertThat(evaluation.system().sample().recall()).isNull();
        assertThat(evaluation.suggestedAuto()).isNull();
        assertThat(evaluation.suggestedReview()).isNull();
        assertThat(evaluation.errors()).isEmpty();
    }

    private void label(long a, long b, boolean same) {
        labels.save(new MatchLabel(OfferPair.of(a, b), FRIDGES, same, new MatchScore(0.5, MatchScore.Reason.TITLE_ONLY),
                T0));
    }

    private long offer(String shop, String externalId, String title, String brand) {
        return catalog.upsertOffer(new OfferUpsertCommand(shop, FRIDGES, externalId,
                "https://example.com/" + externalId, title, brand, null, null, null, T0)).offer().getId();
    }

}
