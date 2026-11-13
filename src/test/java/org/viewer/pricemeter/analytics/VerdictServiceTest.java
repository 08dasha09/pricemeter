package org.viewer.pricemeter.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.viewer.pricemeter.common.Period;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictServiceTest {

    @ParameterizedTest(name = "{0} × {1} → {2}")
    @CsvSource({
            "LOW,    BELOW_MARKET, BELOW_USUAL",
            "LOW,    AT_MARKET,    BELOW_USUAL",
            "LOW,    ABOVE_MARKET, USUAL_CHEAPER_ELSEWHERE",
            "NORMAL, BELOW_MARKET, BELOW_USUAL",
            "NORMAL, AT_MARKET,    USUAL",
            "NORMAL, ABOVE_MARKET, ABOVE_USUAL",
            "HIGH,   BELOW_MARKET, USUAL",
            "HIGH,   AT_MARKET,    ABOVE_USUAL",
            "HIGH,   ABOVE_MARKET, ABOVE_USUAL",
    })
    void bothSignalsFollowTheTable(HistoricalLevel history, MarketPosition market, Verdict.Level expected) {
        Verdict verdict = VerdictService.decide(historical(history, 20, 50.0), market(market));

        assertThat(verdict.level()).isEqualTo(expected);
        assertThat(verdict.confidence()).isEqualTo(Verdict.Confidence.HIGH);
    }

    @ParameterizedTest
    @CsvSource({"LOW, BELOW_USUAL", "NORMAL, USUAL", "HIGH, ABOVE_USUAL"})
    void onlyHistoryGivesReducedConfidence(HistoricalLevel history, Verdict.Level expected) {
        Verdict verdict = VerdictService.decide(historical(history, 30, 50.0), MarketAssessment.UNAVAILABLE);

        assertThat(verdict.level()).isEqualTo(expected);
        assertThat(verdict.confidence()).isEqualTo(Verdict.Confidence.REDUCED);
    }

    @ParameterizedTest
    @CsvSource({"BELOW_MARKET, BELOW_USUAL", "AT_MARKET, USUAL", "ABOVE_MARKET, ABOVE_USUAL"})
    void onlyMarketGivesReducedConfidence(MarketPosition market, Verdict.Level expected) {
        Verdict verdict = VerdictService.decide(historical(HistoricalLevel.INSUFFICIENT_DATA, 5, 50.0),
                market(market));

        assertThat(verdict.level()).isEqualTo(expected);
        assertThat(verdict.confidence()).isEqualTo(Verdict.Confidence.REDUCED);
    }

    @Test
    void noSignalsNoVerdict() {
        Verdict verdict = VerdictService.decide(historical(HistoricalLevel.INSUFFICIENT_DATA, 5, 50.0),
                MarketAssessment.UNAVAILABLE);

        assertThat(verdict.level()).isEqualTo(Verdict.Level.UNKNOWN);
        assertThat(verdict.confidence()).isEqualTo(Verdict.Confidence.NONE);
        assertThat(verdict.explanation())
                .isEqualTo("Інших магазинів з цим товаром у наявності немає, історії замало: 5 з 14 днів за квартал");
    }

    @Test
    void outOfStockHasNoVerdict() {
        Verdict verdict = VerdictService.decide(historical(HistoricalLevel.NOT_AVAILABLE, 30, null),
                market(MarketPosition.BELOW_MARKET));

        assertThat(verdict.level()).isEqualTo(Verdict.Level.UNKNOWN);
        assertThat(verdict.explanation()).isEqualTo("Зараз немає в наявності");
    }

    @Test
    void explainsBothSignals() {
        MarketAssessment market = new MarketAssessment(new BigDecimal("20000"), 3, -12, MarketPosition.BELOW_MARKET);

        Verdict verdict = VerdictService.decide(historical(HistoricalLevel.LOW, 60, 17.6), market);

        assertThat(verdict.explanation())
                .isEqualTo("На 12% дешевше за медіану інших магазинів (3), 18-й перцентиль за квартал");
    }

    @Test
    void marketIsMedianOfOtherShopsOnly() {
        MarketOffer self = offer(1, "MOYO", "18000", true);
        List<MarketOffer> offers = List.of(
                self,
                // друга пропозиція того ж магазину — не ринок
                offer(2, "MOYO", "30000", true),
                offer(3, "CITRUS", "20000", true),
                offer(4, "EPICENTR", "22000", true),
                // без наявності — не враховується
                offer(5, "ELDORADO", "10000", false));

        MarketAssessment market = VerdictService.assessMarket(self, offers);

        assertThat(market.otherMedian()).isEqualByComparingTo("21000");
        assertThat(market.otherShops()).isEqualTo(2);
        assertThat(market.deviationPercent()).isEqualTo(-14);
        assertThat(market.position()).isEqualTo(MarketPosition.BELOW_MARKET);
    }

    @Test
    void noMarketWithoutOtherShopsOrOwnStock() {
        MarketOffer self = offer(1, "MOYO", "18000", true);
        assertThat(VerdictService.assessMarket(self, List.of(self, offer(2, "MOYO", "19000", true))).known())
                .isFalse();

        MarketOffer unavailable = offer(1, "MOYO", "18000", false);
        assertThat(VerdictService.assessMarket(unavailable, List.of(unavailable, offer(3, "CITRUS", "20000", true)))
                .known()).isFalse();
    }

    private static HistoricalAssessment historical(HistoricalLevel level, int days, Double percentile) {
        BigDecimal current = percentile != null ? new BigDecimal("18000") : null;
        PriceStatistics stats = new PriceStatistics(days, null, null, null, null, null, null, current, percentile);
        return new HistoricalAssessment(Period.QUARTER, stats, level, null);
    }

    private static MarketAssessment market(MarketPosition position) {
        int deviation = switch (position) {
            case BELOW_MARKET -> -10;
            case AT_MARKET -> 0;
            case ABOVE_MARKET -> 10;
        };
        return new MarketAssessment(new BigDecimal("20000"), 2, deviation, position);
    }

    private static MarketOffer offer(long id, String shop, String price, boolean available) {
        return new MarketOffer(id, shop, shop, "LG F2Y2HS5WE", "https://shop/" + id, new BigDecimal(price), null,
                available, true, LocalDateTime.of(2026, 10, 11, 4, 0), null, false);
    }

}
