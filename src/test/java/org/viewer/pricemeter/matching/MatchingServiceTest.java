package org.viewer.pricemeter.matching;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.repository.ProductRepository;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.matching.cluster.OfferClusterer;
import org.viewer.pricemeter.matching.normalize.BrandExtractor;
import org.viewer.pricemeter.matching.normalize.ModelCodeExtractor;
import org.viewer.pricemeter.matching.normalize.TitleNormalizer;
import org.viewer.pricemeter.matching.similarity.MatchCandidateFactory;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.matching.similarity.SimilarityScorer;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, CatalogService.class, MatchingService.class, MatchingConfig.class,
        MatchCandidateFactory.class, OfferClusterer.class, SimilarityScorer.class, TitleNormalizer.class,
        BrandExtractor.class, ModelCodeExtractor.class})
class MatchingServiceTest {

    private static final String FRIDGES = "REFRIGERATORS";
    private static final String WASHERS = "WASHING_MACHINES";
    private static final Instant T0 = Instant.parse("2026-10-10T10:00:00Z");

    @Autowired
    private MatchingService matching;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private OfferRepository offers;

    @Autowired
    private ProductRepository products;

    @Test
    void createsProductsAndLinksOffers() {
        long citrus = offer("CITRUS", "C-1", "Двухкамерный холодильник Gorenje RK4182PS4", "GORENJE", null);
        long epicentr = offer("EPICENTR", "E-1", "Холодильник Gorenje RK4182PS4", "Gorenje", null);
        long moyo = offer("MOYO", "M-1", "Холодильник Gorenje RF414EPS4", "Gorenje", "RF414EPS4");

        MatchingReport report = matching.matchCategory("refrigerators");

        assertThat(report).isEqualTo(new MatchingReport(FRIDGES, 3, 2, 1, 2, 0));
        Offer a = offers.findById(citrus).orElseThrow();
        Offer b = offers.findById(epicentr).orElseThrow();
        Offer c = offers.findById(moyo).orElseThrow();
        assertThat(a.getProduct().getId()).isEqualTo(b.getProduct().getId()).isNotEqualTo(c.getProduct().getId());
        assertThat(a.getMatchStatus()).isEqualTo(MatchStatus.AUTO);
        assertThat(a.getMatchConfidence()).isEqualByComparingTo("1.000");
        assertThat(c.getMatchConfidence()).isNull();

        // назва товару — українська, хоча Цитрус назвав російською
        assertThat(a.getProduct().getTitle()).isEqualTo("Холодильник Gorenje RK4182PS4");
        assertThat(a.getProduct().getBrand()).isEqualTo("GORENJE");
        assertThat(a.getProduct().getModelCode()).isEqualTo("RK4182PS4");
        assertThat(c.getProduct().getModelCode()).isEqualTo("RF414EPS4");
    }

    @Test
    void keepsProductIdsBetweenRunsAndDropsEmptyProducts() {
        long citrus = offer("CITRUS", "C-1", "Холодильник Gorenje RK4182PS4", "Gorenje", null);
        long epicentr = offer("EPICENTR", "E-1", "Холодильник Gorenje RK4182PS4", "Gorenje", null);
        matching.matchCategory(FRIDGES);
        long productId = productOf(citrus);
        long otherShop = offer("ELDORADO", "D-1", "Холодильник GORENJE RK4182PS4", "GORENJE", null);

        matching.matchCategory(FRIDGES);

        assertThat(productOf(citrus)).isEqualTo(productId);
        assertThat(productOf(epicentr)).isEqualTo(productId);
        assertThat(productOf(otherShop)).isEqualTo(productId);
        assertThat(products.count()).isEqualTo(1);
    }

    @Test
    void respectsManualDecision() {
        long citrus = offer("CITRUS", "C-1", "Холодильник Gorenje RK4182PS4", "Gorenje", null);
        long epicentr = offer("EPICENTR", "E-1", "Холодильник Gorenje RK4182PS4", "Gorenje", null);
        matching.matchCategory(FRIDGES);
        // модератор виділив пропозицію Епіцентру в окремий товар
        Offer separated = offers.findById(epicentr).orElseThrow();
        separated.setMatchStatus(MatchStatus.REJECTED);
        offers.flush();

        matching.matchCategory(FRIDGES);

        assertThat(productOf(epicentr)).isNotEqualTo(productOf(citrus));
        assertThat(offers.findById(epicentr).orElseThrow().getMatchStatus()).isEqualTo(MatchStatus.REJECTED);
        assertThat(offers.findById(citrus).orElseThrow().getMatchStatus()).isEqualTo(MatchStatus.AUTO);
    }

    @Test
    void uncertainMatchWaitsForReviewWithCandidates() {
        long eldorado = offer("ELDORADO", "D-1", "Пральна машина SAMSUNG WW80T4040CE1UA", "SAMSUNG", null, WASHERS);
        long epicentr = offer("EPICENTR", "E-1", "Пральна машина Samsung WW80T4040", "Samsung", null, WASHERS);

        MatchingReport report = matching.matchCategory(WASHERS);

        assertThat(report.pending()).isEqualTo(2);
        assertThat(productOf(eldorado)).isNotEqualTo(productOf(epicentr));
        List<PendingReview> reviews = matching.pendingReviews(3);
        assertThat(reviews).extracting(PendingReview::offerId).containsExactly(eldorado, epicentr);
        PendingReview review = reviews.get(1);
        assertThat(review.modelCode()).isEqualTo("WW80T4040");
        assertThat(review.suggestions()).singleElement().satisfies(suggestion -> {
            assertThat(suggestion.productId()).isEqualTo(productOf(eldorado));
            assertThat(suggestion.reason()).isEqualTo(MatchScore.Reason.CODE_PREFIX);
            assertThat(suggestion.offers()).extracting(PendingReview.Member::offerId).containsExactly(eldorado);
        });
    }

    @Test
    void manualAssignmentSurvivesRematching() {
        long eldorado = offer("ELDORADO", "D-1", "Пральна машина SAMSUNG WW80T4040CE1UA", "SAMSUNG", null, WASHERS);
        long epicentr = offer("EPICENTR", "E-1", "Пральна машина Samsung WW80T4040", "Samsung", null, WASHERS);
        matching.matchCategory(WASHERS);
        long productId = productOf(eldorado);

        matching.assignToProduct(epicentr, productId);
        matching.matchCategory(WASHERS);

        assertThat(productOf(epicentr)).isEqualTo(productId);
        assertThat(productOf(eldorado)).isEqualTo(productId);
        assertThat(offers.findById(eldorado).orElseThrow().getMatchStatus()).isEqualTo(MatchStatus.MANUAL);
        assertThat(offers.findById(epicentr).orElseThrow().getMatchStatus()).isEqualTo(MatchStatus.MANUAL);
        assertThat(matching.pendingReviews(3)).isEmpty();
        assertThat(products.count()).isEqualTo(1);
    }

    @Test
    void keptSeparateOfferLeavesReview() {
        long eldorado = offer("ELDORADO", "D-1", "Пральна машина SAMSUNG WW80T4040CE1UA", "SAMSUNG", null, WASHERS);
        long epicentr = offer("EPICENTR", "E-1", "Пральна машина Samsung WW80T4040", "Samsung", null, WASHERS);
        matching.matchCategory(WASHERS);

        matching.keepSeparate(epicentr);

        assertThat(offers.findById(epicentr).orElseThrow().getMatchStatus()).isEqualTo(MatchStatus.REJECTED);
        assertThat(offers.findById(eldorado).orElseThrow().getMatchStatus()).isEqualTo(MatchStatus.AUTO);
        assertThat(productOf(epicentr)).isNotEqualTo(productOf(eldorado));
        assertThat(matching.pendingReviews(3)).isEmpty();
    }

    @Test
    void resetDecisionLetsMatchingRejoinOffers() {
        long moyo = offer("MOYO", "M-1", "Пральна машина Samsung WW80T4040CE1UA", "Samsung", "WW80T4040CE1UA", WASHERS);
        long citrus = offer("CITRUS", "C-1", "Samsung WW80T4040CE1UA", "Samsung", null, WASHERS);
        matching.matchCategory(WASHERS);
        long product = productOf(moyo);
        matching.keepSeparate(citrus);
        assertThat(productOf(citrus)).isNotEqualTo(product);

        matching.resetDecision(citrus);

        assertThat(offers.findById(citrus).orElseThrow().getMatchStatus()).isEqualTo(MatchStatus.AUTO);
        assertThat(productOf(citrus)).isEqualTo(product);
        assertThat(productOf(moyo)).isEqualTo(product);
    }

    @Test
    void resettingAutomaticDecisionIsRejected() {
        long moyo = offer("MOYO", "M-1", "Пральна машина Samsung WW80T4040CE1UA", "Samsung", "WW80T4040CE1UA", WASHERS);
        matching.matchCategory(WASHERS);

        assertThatThrownBy(() -> matching.resetDecision(moyo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manuallyPinnedOfferKeepsProductWhenOtherIsDetached() {
        // Цитрус створено раніше (менший id), але товар має лишитись за вручну закріпленою пропозицією Moyo
        long citrus = offer("CITRUS", "C-1", "Samsung WW80T4040CE1UA", "Samsung", null, WASHERS);
        long moyo = offer("MOYO", "M-1", "Пральна машина Samsung WW80T4040CE1UA", "Samsung", "WW80T4040CE1UA", WASHERS);
        matching.matchCategory(WASHERS);
        long product = productOf(moyo);
        matching.assignToProduct(moyo, product);

        // «відкріпити» Цитрус: Moyo лишається MANUAL у тому самому товарі
        matching.keepSeparate(citrus);

        assertThat(productOf(moyo)).isEqualTo(product);
        assertThat(productOf(citrus)).isNotEqualTo(product);
    }

    @Test
    void assigningToProductOfOtherCategoryFails() {
        long washer = offer("ELDORADO", "D-1", "Пральна машина SAMSUNG WW80T4040CE1UA", "SAMSUNG", null, WASHERS);
        long fridge = offer("MOYO", "M-1", "Холодильник Gorenje RF414EPS4", "Gorenje", "RF414EPS4");
        matching.matchAll();

        assertThatThrownBy(() -> matching.assignToProduct(washer, productOf(fridge)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void matchesOnlyKnownCategoriesAfterCrawl() {
        offer("MOYO", "M-1", "Холодильник Gorenje RF414EPS4", "Gorenje", "RF414EPS4");

        List<MatchingReport> reports = matching.matchCategories(Set.of("refrigerators", "TOASTERS"));

        assertThat(reports).extracting(MatchingReport::categoryCode).containsExactly(FRIDGES);
    }

    @Test
    void unknownCategoryIsNotFound() {
        assertThatThrownBy(() -> matching.matchCategory("TOASTERS")).isInstanceOf(NotFoundException.class);
    }

    private long offer(String shop, String externalId, String title, String brand, String mpn) {
        return offer(shop, externalId, title, brand, mpn, FRIDGES);
    }

    private long offer(String shop, String externalId, String title, String brand, String mpn, String category) {
        return catalog.upsertOffer(new OfferUpsertCommand(shop, category, externalId,
                "https://example.com/" + externalId, title, brand, null, mpn, null, T0)).offer().getId();
    }

    private long productOf(long offerId) {
        return offers.findById(offerId).orElseThrow().getProduct().getId();
    }

}
