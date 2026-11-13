package org.viewer.pricemeter.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.common.Period;
import org.viewer.pricemeter.pricehistory.service.DailyPrice;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Статистика цін пропозиції та історична ознака для вердикту.
 */
@Service
@RequiredArgsConstructor
public class PriceAnalyticsService {

    /** Період історичної ознаки за замовчуванням: місяця часто замало, щоб побачити звичайну ціну. */
    public static final Period DEFAULT_PERIOD = Period.QUARTER;

    private final PriceHistoryService priceHistory;
    private final Clock clock;

    public HistoricalAssessment assessHistory(Long offerId, Period period) {
        List<DailyPrice> series = priceHistory.getDailySeries(offerId, period);
        PriceStatistics statistics = PriceStatistics.of(series);
        return new HistoricalAssessment(period, statistics, HistoricalLevel.of(statistics),
                PriceDrop.lastIn(series, LocalDate.now(clock)).orElse(null));
    }

}
