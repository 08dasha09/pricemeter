package org.viewer.pricemeter.analytics;

import org.viewer.pricemeter.pricehistory.service.DailyPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Розподіл денних цін пропозиції за період і місце поточної ціни в ньому. Дні без наявності й непідтверджені дні
 * (магазин не обходили, а ціна тим часом змінилась) не враховуються.
 *
 * @param days              скільки підтверджених днів товар був у наявності з ціною
 * @param current           поточна ціна — стан на останній день ряду; {@code null}, якщо зараз немає в наявності
 * @param currentPercentile перцентиль поточної ціни (0–100) за середнім рангом; {@code null}, якщо поточної ціни
 *                          немає. Незмінна весь період ціна має перцентиль 50.
 */
public record PriceStatistics(
        int days,
        BigDecimal min,
        BigDecimal max,
        BigDecimal mean,
        BigDecimal median,
        BigDecimal p25,
        BigDecimal p75,
        BigDecimal current,
        Double currentPercentile) {

    public static final PriceStatistics EMPTY = new PriceStatistics(0, null, null, null, null, null, null, null, null);

    /**
     * @param series денний ряд за період, від старого до нового ({@code PriceHistoryService.getDailySeries})
     */
    public static PriceStatistics of(List<DailyPrice> series) {
        List<BigDecimal> prices = series.stream()
                .filter(day -> day.confirmed() && day.available() && day.price() != null)
                .map(DailyPrice::price)
                .sorted()
                .toList();
        if (prices.isEmpty()) {
            return EMPTY;
        }
        DailyPrice last = series.getLast();
        BigDecimal current = last.available() ? last.price() : null;
        BigDecimal sum = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PriceStatistics(
                prices.size(),
                prices.getFirst(),
                prices.getLast(),
                sum.divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP),
                PriceStats.quantile(prices, 0.5),
                PriceStats.quantile(prices, 0.25),
                PriceStats.quantile(prices, 0.75),
                current,
                current != null ? PriceStats.percentileRank(prices, current) : null);
    }

}
