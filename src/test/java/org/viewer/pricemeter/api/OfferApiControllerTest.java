package org.viewer.pricemeter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viewer.pricemeter.analytics.PriceAnalyticsService;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.analytics.VerdictService;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.common.Period;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.service.DailyPrice;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OfferApiController.class)
@Import({SecurityConfig.class, OfferApiControllerTest.FixedClock.class, PriceAnalyticsService.class, VerdictService.class, ProductMarketService.class})
class OfferApiControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);

    @TestConfiguration
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-11-04T10:00:00Z"), TimeConfig.ZONE);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private PriceHistoryService priceHistory;

    @Test
    void returnsDailySeriesForPeriod() throws Exception {
        when(priceHistory.getDailySeries(5L, Period.QUARTER)).thenReturn(List.of(
                new DailyPrice(DAY, new BigDecimal("15999.00"), new BigDecimal("18999.00"), true),
                // товару немає — ціна на графіку не показується, навіть якщо магазин її лишив
                new DailyPrice(DAY.plusDays(1), new BigDecimal("15999.00"), null, false),
                // магазин не обходили, а потім ціна змінилась — остання відома, непідтверджена
                new DailyPrice(DAY.plusDays(2), new BigDecimal("15999.00"), null, true, false)));

        mvc.perform(get("/api/offers/5/history").param("period", "QUARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerId").value(5))
                .andExpect(jsonPath("$.period").value("QUARTER"))
                .andExpect(jsonPath("$.points", hasSize(3)))
                .andExpect(jsonPath("$.points[0].confirmed").value(true))
                .andExpect(jsonPath("$.points[2].confirmed").value(false))
                .andExpect(jsonPath("$.points[0].date").value("2026-10-04"))
                .andExpect(jsonPath("$.points[0].price").value(15999.00))
                .andExpect(jsonPath("$.points[0].oldPrice").value(18999.00))
                .andExpect(jsonPath("$.points[0].available").value(true))
                .andExpect(jsonPath("$.points[1].price").value(nullValue()))
                .andExpect(jsonPath("$.points[1].available").value(false));
    }

    @Test
    void defaultsToMonth() throws Exception {
        when(priceHistory.getDailySeries(5L, Period.MONTH)).thenReturn(List.of());

        mvc.perform(get("/api/offers/5/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("MONTH"))
                .andExpect(jsonPath("$.points", hasSize(0)));
        verify(priceHistory).getDailySeries(5L, Period.MONTH);
    }

    @Test
    void unknownOfferGives404() throws Exception {
        when(catalog.getOffer(99L)).thenThrow(NotFoundException.of("Offer", 99L));

        mvc.perform(get("/api/offers/99/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Offer not found: 99"));
    }

    @Test
    void invalidPeriodGives400() throws Exception {
        mvc.perform(get("/api/offers/5/history").param("period", "DECADE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid value of parameter 'period': DECADE"));
    }

    @Test
    void returnsStatisticsForDefaultQuarter() throws Exception {
        List<DailyPrice> days = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            days.add(new DailyPrice(DAY.plusDays(i), BigDecimal.valueOf(20000 - i * 100L), null, true));
        }
        when(priceHistory.getDailySeries(1L, Period.QUARTER)).thenReturn(days);

        mvc.perform(get("/api/offers/1/statistics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("QUARTER"))
                .andExpect(jsonPath("$.days").value(20))
                .andExpect(jsonPath("$.current").value(18100))
                .andExpect(jsonPath("$.currentPercentile").value(2.5))
                .andExpect(jsonPath("$.level").value("LOW"))
                .andExpect(jsonPath("$.minDays").value(14));
        verify(catalog).getOffer(1L);
    }

    @Test
    void shortHistoryHasInsufficientData() throws Exception {
        when(priceHistory.getDailySeries(1L, Period.MONTH)).thenReturn(List.of(
                new DailyPrice(DAY, new BigDecimal("15999"), null, true)));

        mvc.perform(get("/api/offers/1/statistics").param("period", "MONTH"))
                .andExpect(jsonPath("$.level").value("INSUFFICIENT_DATA"))
                .andExpect(jsonPath("$.days").value(1));
    }

    @Test
    void returnsVerdictWithBothSignals() throws Exception {
        Category washers = new Category("WASHING_MACHINES", "Пральні машини");
        Product product = new Product(washers, "Пральна машина LG F2Y2HS5WE");
        product.setId(5L);
        Offer moyo = offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"), product);
        Offer citrus = offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua"), product);
        when(catalog.getOffer(1L)).thenReturn(moyo);
        when(catalog.getOffersOfProduct(5L)).thenReturn(List.of(moyo, citrus));
        Instant now = Instant.parse("2026-10-11T01:00:00Z");
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(
                1L, new PriceSnapshot(1L, new BigDecimal("18000"), null, true, now),
                2L, new PriceSnapshot(2L, new BigDecimal("20000"), null, true, now)));
        // 30 днів по 20 000, сьогодні — 18 000
        List<DailyPrice> days = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            days.add(new DailyPrice(DAY.plusDays(i), new BigDecimal(i < 29 ? "20000" : "18000"), null, true));
        }
        when(priceHistory.getDailySeries(1L, Period.QUARTER)).thenReturn(days);

        mvc.perform(get("/api/offers/1/verdict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("BELOW_USUAL"))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.historicalLevel").value("LOW"))
                .andExpect(jsonPath("$.marketPosition").value("BELOW_MARKET"))
                .andExpect(jsonPath("$.deviationPercent").value(-10))
                .andExpect(jsonPath("$.otherShops").value(1))
                .andExpect(jsonPath("$.explanation").value(
                        "На 10% дешевше за медіану інших магазинів (1), 2-й перцентиль за квартал, "
                                + "останнє зниження — 02.11, на 10% (2 дні тому)"))
                .andExpect(jsonPath("$.lastDropDate").value("2026-11-02"))
                .andExpect(jsonPath("$.lastDropPercent").value(10));
    }

    private static Offer offer(Long id, Shop shop, Product product) {
        Offer offer = new Offer(shop, "ext-" + id, "https://shop/" + id, "LG F2Y2HS5WE");
        offer.setId(id);
        offer.setProduct(product);
        offer.setLastSeenAt(Instant.parse("2026-10-11T01:00:00Z"));
        return offer;
    }

}
