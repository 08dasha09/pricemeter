package org.viewer.pricemeter.fakediscount;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.analytics.MarketOffer;
import org.viewer.pricemeter.fakediscount.rule.InflatedOldPriceRule;
import org.viewer.pricemeter.fakediscount.rule.PromoNotBelowMarketRule;
import org.viewer.pricemeter.fakediscount.rule.RuleHit;
import org.viewer.pricemeter.fakediscount.rule.SamePriceElsewhereRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class FakeDiscountDetectorTest {

    private final FakeDiscountDetector detector = new FakeDiscountDetector(
            List.of(new PromoNotBelowMarketRule(), new InflatedOldPriceRule(), new SamePriceElsewhereRule()), null);

    @Test
    void offersWithoutDeclaredDiscountAreNotAssessed() {
        MarketOffer plain = offer(1, "MOYO", "18000", null, true);
        MarketOffer unavailable = offer(2, "CITRUS", "15000", "20000", false);

        assertThat(detector.assess(plain, List.of(plain, unavailable)).level())
                .isEqualTo(DiscountAssessment.Level.NO_DISCOUNT);
        assertThat(detector.assess(unavailable, List.of(plain, unavailable)).level())
                .isEqualTo(DiscountAssessment.Level.NO_DISCOUNT);
    }

    @Test
    void withoutOtherShopsDiscountCannotBeChecked() {
        MarketOffer promo = offer(1, "MOYO", "18000", "24000", true);
        // друга пропозиція того самого магазину — не ринок
        MarketOffer sameShop = offer(2, "MOYO", "18000", null, true);

        DiscountAssessment assessment = detector.assess(promo, List.of(promo, sameShop));

        assertThat(assessment.level()).isEqualTo(DiscountAssessment.Level.UNKNOWN);
        assertThat(assessment.declaredPercent()).isEqualTo(25);
        assertThat(assessment.marketDeviationPercent()).isNull();
    }

    @Test
    void discountBelowMarketIsGenuine() {
        MarketOffer promo = offer(1, "MOYO", "15000", "17000", true);
        List<MarketOffer> offers = List.of(promo,
                offer(2, "CITRUS", "18000", null, true),
                offer(3, "EPICENTR", "18500", null, true));

        DiscountAssessment assessment = detector.assess(promo, offers);

        assertThat(assessment.level()).isEqualTo(DiscountAssessment.Level.GENUINE);
        assertThat(assessment.hits()).isEmpty();
        assertThat(assessment.otherMedian()).isEqualByComparingTo("18250");
        assertThat(assessment.marketDeviationPercent()).isEqualTo(-18);
    }

    @Test
    void promoAtMarketPriceAloneIsSuspicious() {
        MarketOffer promo = offer(1, "MOYO", "18000", "19000", true);
        // в інших теж «акції», тож правило «та сама ціна без акції» не спрацьовує
        List<MarketOffer> offers = List.of(promo,
                offer(2, "CITRUS", "18300", "19000", true),
                offer(3, "EPICENTR", "17800", "19500", true));

        DiscountAssessment assessment = detector.assess(promo, offers);

        assertThat(assessment.level()).isEqualTo(DiscountAssessment.Level.SUSPICIOUS);
        assertThat(assessment.hits()).extracting(RuleHit::code).containsExactly(PromoNotBelowMarketRule.CODE);
        assertThat(assessment.hits().getFirst().explanation())
                .isEqualTo("Акційна ціна на рівні інших магазинів (0% від медіани) — реальної економії немає");
    }

    @Test
    void inflatedOldPriceAndSamePriceElsewhereMakeFakeDiscount() {
        // «−23%» від 26 000, але в інших магазинах ті самі 20 000 без жодної акції
        MarketOffer promo = offer(1, "MOYO", "20000", "26000", true);
        List<MarketOffer> offers = List.of(promo,
                offer(2, "CITRUS", "20000", null, true),
                offer(3, "EPICENTR", "20100", null, true));

        DiscountAssessment assessment = detector.assess(promo, offers);

        assertThat(assessment.level()).isEqualTo(DiscountAssessment.Level.FAKE);
        assertThat(assessment.declaredPercent()).isEqualTo(23);
        assertThat(assessment.score()).isCloseTo(1.2, within(1e-9));
        assertThat(assessment.hits()).extracting(RuleHit::code).containsExactly(
                PromoNotBelowMarketRule.CODE, InflatedOldPriceRule.CODE, SamePriceElsewhereRule.CODE);
        assertThat(assessment.hits()).extracting(RuleHit::explanation).contains(
                "Заявлена стара ціна на 30% вища за медіану інших магазинів",
                "У 2 з 2 інших магазинів та сама ціна без акції");
    }

    @Test
    void singleOtherShopIsNotEnoughForFake() {
        // ті самі ознаки, що й вище, але ринок — один магазин
        MarketOffer promo = offer(1, "MOYO", "20000", "26000", true);

        DiscountAssessment assessment = detector.assess(promo, List.of(promo, offer(2, "CITRUS", "20000", null, true)));

        assertThat(assessment.score()).isGreaterThanOrEqualTo(FakeDiscountDetector.FAKE_SCORE);
        assertThat(assessment.level()).isEqualTo(DiscountAssessment.Level.SUSPICIOUS);
    }

    @Test
    void promoPriceAboveMarketIsExplainedSeparately() {
        MarketOffer promo = offer(1, "MOYO", "22000", "25000", true);
        List<MarketOffer> offers = List.of(promo, offer(2, "CITRUS", "20000", "21000", true));

        assertThat(detector.assess(promo, offers).hits()).extracting(RuleHit::explanation)
                .contains("Акційна ціна на 10% вища за медіану інших магазинів");
    }

    @Test
    void rulesRespectThresholds() {
        // −5% від ринку — ще «на рівні», −6% — уже дешевше
        assertThat(hitCodes("19000", "30000", "20000", "21000")).contains(PromoNotBelowMarketRule.CODE);
        assertThat(hitCodes("18800", "19000", "20000", "21000")).doesNotContain(PromoNotBelowMarketRule.CODE);
        // стара ціна +15% від медіани — завищена, +14% — ні
        assertThat(hitCodes("15000", "23000", "20000", "21000")).contains(InflatedOldPriceRule.CODE);
        assertThat(hitCodes("15000", "22800", "20000", "21000")).doesNotContain(InflatedOldPriceRule.CODE);
    }

    @Test
    void samePriceRuleNeedsMajorityOfOtherShops() {
        MarketOffer promo = offer(1, "MOYO", "20000", "21000", true);
        // 1 з 2 — не більшість
        assertThat(detector.assess(promo, List.of(promo,
                offer(2, "CITRUS", "20000", null, true),
                offer(3, "EPICENTR", "23000", null, true))).hits())
                .extracting(RuleHit::code).doesNotContain(SamePriceElsewhereRule.CODE);
        // 2 з 3 (±2%) — більшість
        assertThat(detector.assess(promo, List.of(promo,
                offer(2, "CITRUS", "20000", null, true),
                offer(3, "EPICENTR", "20390", null, true),
                offer(4, "ELDORADO", "23000", null, true))).hits())
                .extracting(RuleHit::code).contains(SamePriceElsewhereRule.CODE);
    }

    /** Пропозиція з ціною й старою ціною проти ринку з однією пропозицією CITRUS за {@code market} (з акцією). */
    private List<String> hitCodes(String price, String oldPrice, String market, String marketOld) {
        MarketOffer promo = offer(1, "MOYO", price, oldPrice, true);
        return detector.assess(promo, List.of(promo, offer(2, "CITRUS", market, marketOld, true)))
                .hits().stream().map(RuleHit::code).toList();
    }

    private static MarketOffer offer(long id, String shop, String price, String oldPrice, boolean available) {
        return new MarketOffer(id, shop, shop, "LG F2Y2HS5WE", "https://shop/" + id, new BigDecimal(price),
                oldPrice != null ? new BigDecimal(oldPrice) : null, available, true,
                LocalDateTime.of(2026, 10, 11, 4, 0), null, false);
    }

}
