package org.viewer.pricemeter.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.viewer.pricemeter.pricehistory.service.DailyPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceDropTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    @Test
    void findsLastDropWithDepthAndAge() {
        // 20000 → 18000 (−10%) → 19000 → 17480 (−8%) → без змін
        List<DailyPrice> series = series("20000", "18000", "19000", "17480", "17480");

        PriceDrop drop = PriceDrop.lastIn(series, TODAY).orElseThrow();

        assertThat(drop.date()).isEqualTo(START.plusDays(3));
        assertThat(drop.previousPrice()).isEqualByComparingTo("19000");
        assertThat(drop.price()).isEqualByComparingTo("17480");
        assertThat(drop.depthPercent()).isEqualTo(8);
        assertThat(drop.daysAgo()).isEqualTo(14);
    }

    @Test
    void noDropWhenPriceOnlyGrowsOrStays() {
        assertThat(PriceDrop.lastIn(series("18000", "18000", "19000"), TODAY)).isEmpty();
        assertThat(PriceDrop.lastIn(List.of(), TODAY)).isEmpty();
    }

    @Test
    void tinyChangeDoesNotHideRealDrop() {
        // −10%, а потім 18000 → 17999 (копійки) — останнім лишається справжнє зниження
        PriceDrop drop = PriceDrop.lastIn(series("20000", "18000", "17999"), TODAY).orElseThrow();

        assertThat(drop.date()).isEqualTo(START.plusDays(1));
        assertThat(drop.depthPercent()).isEqualTo(10);
    }

    @Test
    void returnToStockCheaperIsDrop() {
        // день без наявності пропускається: порівнюємо з останньою ціною в наявності
        PriceDrop drop = PriceDrop.lastIn(series("20000", null, "17000"), TODAY).orElseThrow();

        assertThat(drop.date()).isEqualTo(START.plusDays(2));
        assertThat(drop.previousPrice()).isEqualByComparingTo("20000");
        assertThat(drop.depthPercent()).isEqualTo(15);
    }

    @ParameterizedTest
    @CsvSource({"0, сьогодні", "1, вчора", "2, 2 дні тому", "4, 4 дні тому", "5, 5 днів тому", "11, 11 днів тому",
            "14, 14 днів тому", "21, 21 день тому", "22, 22 дні тому", "111, 111 днів тому"})
    void daysAgoInUkrainian(long days, String expected) {
        assertThat(VerdictService.daysAgo(days)).isEqualTo(expected);
    }

    @Test
    void explanationMentionsLastDrop() {
        PriceStatistics stats = new PriceStatistics(20, null, null, null, null, null, null,
                new BigDecimal("17480"), 18.0);
        PriceDrop drop = new PriceDrop(LocalDate.of(2026, 10, 12), new BigDecimal("19000"), new BigDecimal("17480"),
                8, 6);
        HistoricalAssessment historical = new HistoricalAssessment(org.viewer.pricemeter.common.Period.QUARTER, stats,
                HistoricalLevel.LOW, drop);

        assertThat(VerdictService.explain(historical, MarketAssessment.UNAVAILABLE)).isEqualTo(
                "Інших магазинів з цим товаром у наявності немає, 18-й перцентиль за квартал, "
                        + "останнє зниження — 12.10, на 8% (6 днів тому)");
    }

    /** Денний ряд від {@link #START}; {@code null} — день без наявності. */
    private static List<DailyPrice> series(String... prices) {
        List<DailyPrice> days = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            BigDecimal price = prices[i] != null ? new BigDecimal(prices[i]) : null;
            days.add(new DailyPrice(START.plusDays(i), price, null, price != null));
        }
        return days;
    }

}
