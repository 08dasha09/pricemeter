package org.viewer.pricemeter.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.viewer.pricemeter.analytics.PriceAnalyticsService;
import org.viewer.pricemeter.analytics.VerdictService;
import org.viewer.pricemeter.api.dto.OfferStatisticsDto;
import org.viewer.pricemeter.api.dto.PriceHistoryDto;
import org.viewer.pricemeter.api.dto.PricePointDto;
import org.viewer.pricemeter.api.dto.VerdictDto;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.Period;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferApiController {

    private final CatalogService catalog;
    private final PriceHistoryService priceHistory;
    private final PriceAnalyticsService analytics;
    private final VerdictService verdicts;

    /**
     * Приклад: {@code GET /api/offers/1/history?period=QUARTER}
     */
    @GetMapping("/{id}/history")
    public PriceHistoryDto history(@PathVariable Long id, @RequestParam(defaultValue = "MONTH") Period period) {
        catalog.getOffer(id);
        return new PriceHistoryDto(id, period, priceHistory.getDailySeries(id, period).stream()
                .map(PricePointDto::of)
                .toList());
    }

    /**
     * Розподіл цін за період і історична ознака поточної ціни.
     * Приклад: {@code GET /api/offers/1/statistics?period=QUARTER}
     */
    /**
     * Вердикт щодо поточної ціни. Приклад: {@code GET /api/offers/1/verdict?period=QUARTER}
     */
    @GetMapping("/{id}/verdict")
    public VerdictDto verdict(@PathVariable Long id, @RequestParam(required = false) Period period) {
        return VerdictDto.of(id, verdicts.forOffer(id, period != null ? period : PriceAnalyticsService.DEFAULT_PERIOD));
    }

    @GetMapping("/{id}/statistics")
    public OfferStatisticsDto statistics(@PathVariable Long id, @RequestParam(required = false) Period period) {
        catalog.getOffer(id);
        return OfferStatisticsDto.of(id,
                analytics.assessHistory(id, period != null ? period : PriceAnalyticsService.DEFAULT_PERIOD));
    }

}
