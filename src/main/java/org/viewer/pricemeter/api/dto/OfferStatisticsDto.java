package org.viewer.pricemeter.api.dto;

import org.viewer.pricemeter.analytics.HistoricalAssessment;
import org.viewer.pricemeter.analytics.HistoricalLevel;
import org.viewer.pricemeter.analytics.PriceStatistics;
import org.viewer.pricemeter.common.Period;

import java.math.BigDecimal;

/**
 * Розподіл цін пропозиції за період і історична ознака поточної ціни.
 *
 * @param days              днів у наявності з ціною за період
 * @param currentPercentile перцентиль поточної ціни (0–100); {@code null} — зараз немає в наявності
 * @param level             LOW / NORMAL / HIGH; INSUFFICIENT_DATA — днів менше {@code minDays}
 * @param minDays           скільки днів потрібно для оцінки
 */
public record OfferStatisticsDto(
        Long offerId,
        Period period,
        int days,
        BigDecimal min,
        BigDecimal max,
        BigDecimal mean,
        BigDecimal median,
        BigDecimal p25,
        BigDecimal p75,
        BigDecimal current,
        Double currentPercentile,
        HistoricalLevel level,
        int minDays) {

    public static OfferStatisticsDto of(Long offerId, HistoricalAssessment assessment) {
        PriceStatistics s = assessment.statistics();
        return new OfferStatisticsDto(offerId, assessment.period(), s.days(), s.min(), s.max(), s.mean(), s.median(),
                s.p25(), s.p75(), s.current(), s.currentPercentile(), assessment.level(), HistoricalLevel.MIN_DAYS);
    }

}
