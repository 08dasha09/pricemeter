package org.viewer.pricemeter.matching.similarity;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.matching.normalize.BrandExtractor;
import org.viewer.pricemeter.matching.normalize.ModelCodeExtractor;
import org.viewer.pricemeter.matching.normalize.TitleNormalizer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Пари — реальні назви з БД; синтетичні позначено окремо.
 */
class SimilarityScorerTest {

    private static final String WASHERS = "WASHING_MACHINES";
    private static final String FRIDGES = "REFRIGERATORS";
    private static final String DISHWASHERS = "DISHWASHERS";

    private final BrandExtractor brands = new BrandExtractor();
    private final MatchCandidateFactory factory = new MatchCandidateFactory(
            new TitleNormalizer(), brands, new ModelCodeExtractor(brands));
    private final SimilarityScorer scorer = new SimilarityScorer();

    @Test
    void sameCodeInDifferentShopsIsExactMatch() {
        MatchScore score = score(
                offer("CITRUS", FRIDGES, "Двухкамерный холодильник Gorenje RK4182PS4", "GORENJE", null),
                offer("EPICENTR", FRIDGES, "Холодильник Gorenje RK4182PS4", "Gorenje", null));

        assertThat(score.reason()).isEqualTo(MatchScore.Reason.EXACT_CODE);
        assertThat(score.value()).isGreaterThanOrEqualTo(SimilarityScorer.EXACT_CODE_MIN);
    }

    @Test
    void mpnMatchesCodeFromTitle() {
        MatchScore score = score(
                offer("CITRUS", WASHERS, "Пральна машина LG F2Y2HS5WE", "LG", null),
                offer("MOYO", WASHERS, "Пральна машина LG F2Y2HS5WE", "LG", "F2Y2HS5WE"));

        assertThat(score.reason()).isEqualTo(MatchScore.Reason.EXACT_CODE);
    }

    @Test
    void differentlyWrittenCodesMatch() {
        // синтетичний приклад: та сама модель, записана по-різному
        assertThat(score(
                offer("A", WASHERS, "Samsung WW80T4040CE1UA", "Samsung", null),
                offer("B", WASHERS, "Пральна машина Samsung WW80T4040 CE1/UA", null, null)).reason())
                .isEqualTo(MatchScore.Reason.EXACT_CODE);
        assertThat(score(
                offer("ELDORADO", WASHERS, "Пральна машина WHIRLPOOL WRSB 7259 WB UA", "WHIRLPOOL", null),
                offer("B", WASHERS, "Пральна машина Whirlpool WRSB7259WBUA", "Whirlpool", null)).reason())
                .isEqualTo(MatchScore.Reason.EXACT_CODE);
    }

    @Test
    void sameModelForAnotherRegionMatchesByBaseCode() {
        MatchScore score = score(
                offer("EPICENTR", WASHERS, "Пральна машина Samsung WW60A3120BE/LE", "Samsung", null),
                offer("B", WASHERS, "Пральна машина Samsung WW60A3120BE/UA", "Samsung", null));

        assertThat(score.reason()).isEqualTo(MatchScore.Reason.BASE_CODE);
        assertThat(score.value()).isBetween(SimilarityScorer.BASE_CODE_MIN, SimilarityScorer.BASE_CODE_MAX);
    }

    @Test
    void codeWithoutSuffixIsOnlyPrefixMatch() {
        MatchScore score = score(
                offer("A", WASHERS, "Пральна машина Samsung WW80T4040", "Samsung", null),
                offer("B", WASHERS, "Пральна машина Samsung WW80T4040CE1UA", "Samsung", null));

        assertThat(score.reason()).isEqualTo(MatchScore.Reason.CODE_PREFIX);
        assertThat(score.value()).isBetween(SimilarityScorer.CODE_PREFIX_MIN, SimilarityScorer.CODE_PREFIX_MAX);
    }

    @Test
    void neighbouringModificationsAreDifferent() {
        // назви майже однакові, але це різні моделі
        MatchScore score = score(
                offer("MOYO", WASHERS, "Пральна машина LG F2Y2HS5WE", "LG", "F2Y2HS5WE"),
                offer("MOYO", WASHERS, "Пральна машина LG F2Y2HS6WE", "LG", "F2Y2HS6WE"));

        assertThat(score.reason()).isEqualTo(MatchScore.Reason.DIFFERENT_CODE);
        assertThat(score.value()).isLessThanOrEqualTo(SimilarityScorer.DIFFERENT_CODE_MAX);
    }

    @Test
    void sameDigitsWithOtherLettersNeedReview() {
        // колір: S — срібний, W — білий
        MatchScore score = score(
                offer("ELDORADO", DISHWASHERS, "Посудомийна машина GORENJE GS520E15S", "GORENJE", null),
                offer("ELDORADO", DISHWASHERS, "Посудомийна машина GORENJE GS520E15W", "GORENJE", null));

        assertThat(score.reason()).isEqualTo(MatchScore.Reason.SIMILAR_CODE);
        assertThat(score.value()).isGreaterThan(SimilarityScorer.DIFFERENT_CODE_MAX)
                .isLessThanOrEqualTo(SimilarityScorer.SIMILAR_CODE_MAX);
    }

    @Test
    void withoutCodeOnlyTitleCounts() {
        MatchScore score = score(
                offer("A", WASHERS, "Пральна машина Samsung", "Samsung", null),
                offer("B", WASHERS, "Пральна машина Samsung WW80T4040CE1UA", "Samsung", null));

        assertThat(score.reason()).isEqualTo(MatchScore.Reason.TITLE_ONLY);
        assertThat(score.value()).isLessThan(SimilarityScorer.TITLE_ONLY_MAX);
    }

    @Test
    void differentBrandOrCategoryIsBlocked() {
        assertThat(score(
                offer("A", FRIDGES, "Холодильник Gorenje RK4182PS4", "Gorenje", null),
                offer("B", FRIDGES, "Холодильник Bosch RK4182PS4", "Bosch", null)))
                .isEqualTo(new MatchScore(0, MatchScore.Reason.BLOCKED));
        assertThat(score(
                offer("A", FRIDGES, "Холодильник Gorenje RK4182PS4", "Gorenje", null),
                offer("B", WASHERS, "Холодильник Gorenje RK4182PS4", "Gorenje", null)))
                .isEqualTo(new MatchScore(0, MatchScore.Reason.BLOCKED));
    }

    @Test
    void equalGtinIsMatchRegardlessOfTitle() {
        MatchScore score = score(
                offer("A", WASHERS, "Пральна машина LG", "LG", null, "08806098765432"),
                offer("B", WASHERS, "LG прально-сушильна", "LG", null, "8806098765432"));

        assertThat(score).isEqualTo(new MatchScore(1, MatchScore.Reason.GTIN));
    }

    @Test
    void differentGtinOverridesSameCode() {
        MatchScore score = score(
                offer("A", WASHERS, "Пральна машина LG F2Y2HS5WE", "LG", null, "8806098765432"),
                offer("B", WASHERS, "Пральна машина LG F2Y2HS5WE", "LG", null, "8806098765449"));

        assertThat(score.reason()).isEqualTo(MatchScore.Reason.GTIN_CONFLICT);
        assertThat(score.value()).isLessThanOrEqualTo(SimilarityScorer.GTIN_CONFLICT_MAX);
    }

    @Test
    void scoreIsSymmetric() {
        List<MatchCandidate> candidates = factory.build(List.of(
                offer("ELDORADO", WASHERS, "Пральна машина SAMSUNG WW90DG6G94LKUA", "SAMSUNG", null),
                offer("ELDORADO", WASHERS, "Пральна машина SAMSUNG WW90DG6G94LBUA", "SAMSUNG", null)));

        assertThat(scorer.score(candidates.get(0), candidates.get(1)))
                .isEqualTo(scorer.score(candidates.get(1), candidates.get(0)));
    }

    private MatchScore score(OfferText a, OfferText b) {
        List<MatchCandidate> candidates = factory.build(List.of(a, b));
        return scorer.score(candidates.get(0), candidates.get(1));
    }

    private long nextId = 1;

    private OfferText offer(String shop, String category, String title, String brandRaw, String mpn) {
        return offer(shop, category, title, brandRaw, mpn, null);
    }

    private OfferText offer(String shop, String category, String title, String brandRaw, String mpn,
                                   String gtin) {
        return new OfferText(nextId++, shop, category, title, brandRaw, mpn, gtin);
    }

}
