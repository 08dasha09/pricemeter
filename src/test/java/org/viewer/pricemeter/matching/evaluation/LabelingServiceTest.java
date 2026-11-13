package org.viewer.pricemeter.matching.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.matching.normalize.BrandExtractor;
import org.viewer.pricemeter.matching.normalize.ModelCodeExtractor;
import org.viewer.pricemeter.matching.normalize.TitleNormalizer;
import org.viewer.pricemeter.matching.similarity.MatchCandidateFactory;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.matching.similarity.SimilarityScorer;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogService.class, LabelingService.class, PairScanner.class,
        MatchCandidateFactory.class, SimilarityScorer.class, TitleNormalizer.class, BrandExtractor.class,
        ModelCodeExtractor.class, LabelingServiceTest.FixedClock.class})
class LabelingServiceTest {

    private static final String FRIDGES = "REFRIGERATORS";
    private static final Instant T0 = Instant.parse("2026-10-10T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, TimeConfig.ZONE);
        }
    }

    @Autowired
    private LabelingService labeling;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private MatchLabelRepository labels;

    @Test
    void offersCrossShopPairsFromLeastLabeledBand() {
        long citrus = offer("CITRUS", "C-1", "Холодильник Gorenje RK4182PS4", "Gorenje", FRIDGES);
        long epicentr = offer("EPICENTR", "E-1", "Холодильник Gorenje RK4182PS4", "Gorenje", FRIDGES);
        // той самий магазин, що й перша пропозиція, — з нею не порівнюється
        long citrusOther = offer("CITRUS", "C-2", "Холодильник Gorenje NRK6192AW4", "Gorenje", FRIDGES);
        // інший бренд — ні з чим не порівнюється
        offer("MOYO", "M-1", "Холодильник Bosch KGN39VLEB", "Bosch", FRIDGES);

        // за рівної кількості міток — вищий діапазон
        LabelingTask first = labeling.nextTask().orElseThrow();
        assertThat(Set.of(first.a().offerId(), first.b().offerId())).containsExactlyInAnyOrder(citrus, epicentr);
        assertThat(first.band()).isEqualTo(ScoreBand.AUTO_HIGH);
        assertThat(first.score().reason()).isEqualTo(MatchScore.Reason.EXACT_CODE);
        assertThat(first.unlabeledLeft()).isEqualTo(1);
        assertThat(first.a().modelCode()).isEqualTo("RK4182PS4");
        assertThat(first.categoryCode()).isEqualTo(FRIDGES);

        labeling.label(citrus, epicentr, true);

        LabelingTask second = labeling.nextTask().orElseThrow();
        assertThat(Set.of(second.a().offerId(), second.b().offerId()))
                .containsExactlyInAnyOrder(citrusOther, epicentr);
        assertThat(second.score().reason()).isEqualTo(MatchScore.Reason.DIFFERENT_CODE);
        assertThat(second.band()).isEqualTo(ScoreBand.VERY_LOW);

        labeling.label(epicentr, citrusOther, false);

        assertThat(labeling.nextTask()).isEmpty();
    }

    @Test
    void doesNotPairOffersOfDifferentCategories() {
        offer("CITRUS", "C-1", "Холодильник Gorenje RK4182PS4", "Gorenje", FRIDGES);
        offer("EPICENTR", "E-1", "Пральна машина Gorenje WNEI84APS", "Gorenje", "WASHING_MACHINES");

        assertThat(labeling.nextTask()).isEmpty();
    }

    @Test
    void storesOrderedPairAndRelabels() {
        long citrus = offer("CITRUS", "C-1", "Холодильник Gorenje RK4182PS4", "Gorenje", FRIDGES);
        long epicentr = offer("EPICENTR", "E-1", "Холодильник Gorenje RK4182PS4", "Gorenje", FRIDGES);

        labeling.label(epicentr, citrus, false);
        labeling.label(citrus, epicentr, true);

        assertThat(labels.findAll()).singleElement().satisfies(label -> {
            assertThat(label.getOfferAId()).isEqualTo(Math.min(citrus, epicentr));
            assertThat(label.getOfferBId()).isEqualTo(Math.max(citrus, epicentr));
            assertThat(label.isSameProduct()).isTrue();
            assertThat(label.getCategoryCode()).isEqualTo(FRIDGES);
            assertThat(label.getScore()).isEqualByComparingTo("1.000");
            assertThat(label.getReason()).isEqualTo(MatchScore.Reason.EXACT_CODE);
            assertThat(label.getLabeledAt()).isEqualTo(NOW);
        });

        LabelingProgress progress = labeling.progress();
        assertThat(progress.total()).isEqualTo(1);
        assertThat(progress.same()).isEqualTo(1);
        assertThat(progress.bands()).hasSize(ScoreBand.values().length);
        assertThat(progress.bands().get(ScoreBand.AUTO_HIGH)).isEqualTo(new LabelingProgress.Band(1, 1));
        assertThat(progress.bands().get(ScoreBand.VERY_LOW)).isEqualTo(new LabelingProgress.Band(0, 0));
    }

    @Test
    void rejectsPairsThatAreNeverMatched() {
        long citrus = offer("CITRUS", "C-1", "Холодильник Gorenje RK4182PS4", "Gorenje", FRIDGES);
        long citrusOther = offer("CITRUS", "C-2", "Холодильник Gorenje RK4182PS4", "Gorenje", FRIDGES);
        long washer = offer("EPICENTR", "E-1", "Пральна машина Gorenje WNEI84APS", "Gorenje", "WASHING_MACHINES");

        assertThatThrownBy(() -> labeling.label(citrus, citrusOther, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("одного магазину");
        assertThatThrownBy(() -> labeling.label(citrus, washer, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("різних категорій");
        assertThatThrownBy(() -> labeling.label(citrus, citrus, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(labels.count()).isZero();
    }

    private long offer(String shop, String externalId, String title, String brand, String category) {
        return catalog.upsertOffer(new OfferUpsertCommand(shop, category, externalId,
                "https://example.com/" + externalId, title, brand, null, null, null, T0)).offer().getId();
    }

}
