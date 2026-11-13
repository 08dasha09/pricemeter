package org.viewer.pricemeter.analytics;

import org.viewer.pricemeter.common.Period;

/**
 * Історична оцінка поточної ціни пропозиції за період.
 *
 * @param lastDrop останнє зниження ціни за період; {@code null}, якщо ціна не знижувалась
 */
public record HistoricalAssessment(Period period, PriceStatistics statistics, HistoricalLevel level,
                                   PriceDrop lastDrop) {
}
