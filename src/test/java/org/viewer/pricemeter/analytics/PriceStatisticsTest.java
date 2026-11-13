package org.viewer.pricemeter.analytics;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.pricehistory.service.DailyPrice;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceStatisticsTest {

    private static final LocalDate START = LocalDate.of(2026, 7, 1);

    @Test
    void unchangedPriceIsNormalNotLow() {
        PriceStatistics stats = PriceStatistics.of(series(repeat(15999, 14)));

        assertThat(stats.days()).isEqualTo(14);
        assertThat(stats.min()).isEqualByComparingTo("15999");
        assertThat(stats.p25()).isEqualByComparingTo("15999");
        assertThat(stats.p75()).isEqualByComparingTo("15999");
        assertThat(stats.currentPercentile()).isEqualTo(50.0);
        assertThat(HistoricalLevel.of(stats)).isEqualTo(HistoricalLevel.NORMAL);
    }

    @Test
    void computesQuantilesWithInterpolation() {
        // 1 000, 2 000, …, 20 000; поточна — остання, найвища
        List<Integer> prices = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            prices.add(i * 1000);
        }

        PriceStatistics stats = PriceStatistics.of(series(prices));

        assertThat(stats.min()).isEqualByComparingTo("1000");
        assertThat(stats.max()).isEqualByComparingTo("20000");
        assertThat(stats.mean()).isEqualByComparingTo("10500");
        assertThat(stats.median()).isEqualByComparingTo("10500");
        assertThat(stats.p25()).isEqualByComparingTo("5750");
        assertThat(stats.p75()).isEqualByComparingTo("15250");
        assertThat(stats.current()).isEqualByComparingTo("20000");
        assertThat(stats.currentPercentile()).isEqualTo(97.5);
        assertThat(HistoricalLevel.of(stats)).isEqualTo(HistoricalLevel.HIGH);
    }

    @Test
    void lowestPriceIsLow() {
        List<Integer> prices = new ArrayList<>(repeat(20000, 19));
        prices.add(17000);

        PriceStatistics stats = PriceStatistics.of(series(prices));

        assertThat(stats.currentPercentile()).isEqualTo(2.5);
        assertThat(HistoricalLevel.of(stats)).isEqualTo(HistoricalLevel.LOW);
    }

    @Test
    void percentile25And75AreStillNormal() {
        // 4 дні нижче, 2 дні — поточна ціна, 14 днів вище: (4 + 2/2) / 20 = 25-й перцентиль
        List<Integer> at25 = new ArrayList<>(repeat(100, 4));
        at25.addAll(repeat(300, 14));
        at25.addAll(repeat(200, 2));
        PriceStatistics p25 = PriceStatistics.of(series(at25));
        assertThat(p25.currentPercentile()).isEqualTo(25.0);
        assertThat(HistoricalLevel.of(p25)).isEqualTo(HistoricalLevel.NORMAL);

        // 14 нижче, 2 — поточна, 4 вище: (14 + 1) / 20 = 75-й
        List<Integer> at75 = new ArrayList<>(repeat(100, 14));
        at75.addAll(repeat(300, 4));
        at75.addAll(repeat(200, 2));
        PriceStatistics p75 = PriceStatistics.of(series(at75));
        assertThat(p75.currentPercentile()).isEqualTo(75.0);
        assertThat(HistoricalLevel.of(p75)).isEqualTo(HistoricalLevel.NORMAL);
    }

    @Test
    void justBelow25IsLow() {
        // (4 + 1/2) / 20 = 22.5
        List<Integer> prices = new ArrayList<>(repeat(100, 4));
        prices.addAll(repeat(300, 15));
        prices.add(200);

        PriceStatistics stats = PriceStatistics.of(series(prices));

        assertThat(stats.currentPercentile()).isEqualTo(22.5);
        assertThat(HistoricalLevel.of(stats)).isEqualTo(HistoricalLevel.LOW);
    }

    @Test
    void daysWithoutStockAreIgnored() {
        List<DailyPrice> days = new ArrayList<>();
        for (int i = 0; i < 17; i++) {
            // дні 3–5 — без наявності, з «ціною», яка не повинна потрапити в розподіл
            boolean available = i < 3 || i > 5;
            days.add(new DailyPrice(START.plusDays(i), new BigDecimal(available ? "10000" : "1"), null, available));
        }

        PriceStatistics stats = PriceStatistics.of(days);

        assertThat(stats.days()).isEqualTo(14);
        assertThat(stats.min()).isEqualByComparingTo("10000");
        assertThat(HistoricalLevel.of(stats)).isEqualTo(HistoricalLevel.NORMAL);
    }

    @Test
    void unconfirmedDaysAreNotHistory() {
        List<DailyPrice> days = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            // дні 5–8 магазин не обходили, а потім ціна змінилась — «днів історії» з них немає
            boolean confirmed = i < 5 || i > 8;
            days.add(new DailyPrice(START.plusDays(i), new BigDecimal(i <= 8 ? "10000" : "9000"), null, true,
                    confirmed));
        }

        PriceStatistics stats = PriceStatistics.of(days);

        assertThat(stats.days()).isEqualTo(12);
        assertThat(HistoricalLevel.of(stats)).isEqualTo(HistoricalLevel.INSUFFICIENT_DATA);
    }

    @Test
    void currentlyUnavailableHasNoCurrentPrice() {
        List<DailyPrice> days = new ArrayList<>(series(repeat(10000, 20)));
        days.add(new DailyPrice(START.plusDays(20), new BigDecimal("10000"), null, false));

        PriceStatistics stats = PriceStatistics.of(days);

        assertThat(stats.days()).isEqualTo(20);
        assertThat(stats.current()).isNull();
        assertThat(stats.currentPercentile()).isNull();
        assertThat(HistoricalLevel.of(stats)).isEqualTo(HistoricalLevel.NOT_AVAILABLE);
    }

    @Test
    void shortHistoryIsInsufficient() {
        assertThat(HistoricalLevel.of(PriceStatistics.of(series(repeat(10000, 13)))))
                .isEqualTo(HistoricalLevel.INSUFFICIENT_DATA);
        assertThat(PriceStatistics.of(List.of())).isEqualTo(PriceStatistics.EMPTY);
        assertThat(HistoricalLevel.of(PriceStatistics.EMPTY)).isEqualTo(HistoricalLevel.INSUFFICIENT_DATA);
        assertThat(HistoricalLevel.INSUFFICIENT_DATA.known()).isFalse();
        assertThat(HistoricalLevel.LOW.known()).isTrue();
    }

    /** Ряд за послідовні дні; останнє значення — поточна ціна. */
    private static List<DailyPrice> series(List<Integer> prices) {
        List<DailyPrice> days = new ArrayList<>();
        for (int i = 0; i < prices.size(); i++) {
            days.add(new DailyPrice(START.plusDays(i), BigDecimal.valueOf(prices.get(i)), null, true));
        }
        return days;
    }

    private static List<Integer> repeat(int price, int times) {
        return new ArrayList<>(Collections.nCopies(times, price));
    }

}
