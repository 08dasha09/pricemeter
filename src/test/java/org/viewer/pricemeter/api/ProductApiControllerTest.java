package org.viewer.pricemeter.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viewer.pricemeter.analytics.PriceAnalyticsService;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.analytics.VerdictService;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.fakediscount.FakeDiscountDetector;
import org.viewer.pricemeter.fakediscount.rule.InflatedOldPriceRule;
import org.viewer.pricemeter.fakediscount.rule.PromoNotBelowMarketRule;
import org.viewer.pricemeter.fakediscount.rule.SamePriceElsewhereRule;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.ProductFilter;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.common.Period;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.service.DailyPrice;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductApiController.class)
@Import({SecurityConfig.class, TimeConfig.class, ProductMarketService.class, VerdictService.class, PriceAnalyticsService.class, FakeDiscountDetector.class,
        PromoNotBelowMarketRule.class, InflatedOldPriceRule.class, SamePriceElsewhereRule.class})
class ProductApiControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 4);
    private static final Instant SEEN = Instant.parse("2026-10-06T01:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private PriceHistoryService priceHistory;

    @Test
    void alignsShopSeriesOnCommonDates() throws Exception {
        Category washers = new Category("WASHING_MACHINES", "Пральні машини");
        Product product = new Product(washers, "Пральна машина LG F2Y2HS5WE");
        product.setId(5L);
        when(catalog.getProduct(5L)).thenReturn(product);
        when(catalog.getOffersOfProduct(5L)).thenReturn(List.of(
                offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua")),
                offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua")),
                offer(3L, new Shop("ELDORADO", "Eldorado", "https://eldorado.ua"))));
        // Moyo — з 4-го, Цитрус — з 5-го і 6-го немає в наявності, Eldorado — без історії за період
        when(priceHistory.getDailySeries(1L, Period.QUARTER)).thenReturn(List.of(
                day(DAY, "20799", true), day(DAY.plusDays(1), "20799", true), day(DAY.plusDays(2), "19999", true)));
        when(priceHistory.getDailySeries(2L, Period.QUARTER)).thenReturn(List.of(
                day(DAY.plusDays(1), "20499", true), day(DAY.plusDays(2), "20499", false)));
        when(priceHistory.getDailySeries(3L, Period.QUARTER)).thenReturn(List.of());

        mvc.perform(get("/api/products/5/history").param("period", "QUARTER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(5))
                .andExpect(jsonPath("$.period").value("QUARTER"))
                .andExpect(jsonPath("$.dates", contains("2026-10-04", "2026-10-05", "2026-10-06")))
                .andExpect(jsonPath("$.series", hasSize(2)))
                // за назвою магазину: Moyo, Цитрус
                .andExpect(jsonPath("$.series[0].shopCode").value("MOYO"))
                .andExpect(jsonPath("$.series[0].prices", contains(20799, 20799, 19999)))
                .andExpect(jsonPath("$.series[1].shopName").value("Цитрус"))
                .andExpect(jsonPath("$.series[1].prices[0]").value(nullValue()))
                .andExpect(jsonPath("$.series[1].prices[1]").value(20499))
                .andExpect(jsonPath("$.series[1].prices[2]").value(nullValue()))
                // немає даних (Цитрус 4-го) — null; немає в наявності (6-го) — день є, підтверджений
                .andExpect(jsonPath("$.series[1].confirmed", contains(null, true, true)))
                .andExpect(jsonPath("$.series[0].confirmed", contains(true, true, true)));
    }

    @Test
    void searchesProductsWithMarketSummary() throws Exception {
        Category washers = new Category("WASHING_MACHINES", "Пральні машини");
        Product product = new Product(washers, "Пральна машина LG F2Y2HS5WE");
        product.setId(5L);
        product.setModelCode("F2Y2HS5WE");
        Offer moyo = offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"));
        Offer citrus = offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua"));
        moyo.setProduct(product);
        citrus.setProduct(product);
        when(catalog.searchProducts(eq(new ProductFilter("WASHING_MACHINES", "f2y2", true)), any()))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(0, 20), 1));
        when(catalog.getOffersOfProducts(List.of(5L))).thenReturn(List.of(moyo, citrus));
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(
                1L, snapshot(1L, "20799", true), 2L, snapshot(2L, "20499", true)));

        mvc.perform(get("/api/products").param("query", "f2y2").param("category", "WASHING_MACHINES")
                        .param("multiShop", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(5))
                .andExpect(jsonPath("$.content[0].modelCode").value("F2Y2HS5WE"))
                .andExpect(jsonPath("$.content[0].shopCodes", contains("MOYO", "CITRUS")))
                .andExpect(jsonPath("$.content[0].minPrice").value(20499))
                .andExpect(jsonPath("$.content[0].purchasableShops").value(2));

        mvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsCurrentOffersAgainstMedian() throws Exception {
        Category washers = new Category("WASHING_MACHINES", "Пральні машини");
        Product product = new Product(washers, "Пральна машина LG F2Y2HS5WE");
        product.setId(5L);
        when(catalog.getProduct(5L)).thenReturn(product);
        when(catalog.getOffersOfProduct(5L)).thenReturn(List.of(
                offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua")),
                offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua")),
                offer(3L, new Shop("EPICENTR", "Епіцентр", "https://epicentrk.ua"))));
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(
                1L, snapshot(1L, "22000", true), 2L, snapshot(2L, "20000", true), 3L, snapshot(3L, "18000", true)));

        mvc.perform(get("/api/products/5/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.median").value(20000))
                .andExpect(jsonPath("$.minPrice").value(18000))
                .andExpect(jsonPath("$.offers[0].shopCode").value("EPICENTR"))
                .andExpect(jsonPath("$.offers[0].deviationPercent").value(-10))
                .andExpect(jsonPath("$.offers[0].position").value("BELOW_MARKET"))
                .andExpect(jsonPath("$.offers[0].cheapest").value(true))
                .andExpect(jsonPath("$.offers[1].position").value("AT_MARKET"))
                .andExpect(jsonPath("$.offers[2].position").value("ABOVE_MARKET"));
    }

    @Test
    void returnsVerdictOfCheapestOfferAndAllOffers() throws Exception {
        Category washers = new Category("WASHING_MACHINES", "Пральні машини");
        Product product = new Product(washers, "Пральна машина LG F2Y2HS5WE");
        product.setId(5L);
        when(catalog.getProduct(5L)).thenReturn(product);
        when(catalog.getOffersOfProduct(5L)).thenReturn(List.of(
                offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua")),
                offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua")),
                offer(3L, new Shop("EPICENTR", "Епіцентр", "https://epicentrk.ua"))));
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(
                1L, snapshot(1L, "22000", true), 2L, snapshot(2L, "20000", true), 3L, snapshot(3L, "18000", true)));

        mvc.perform(get("/api/products/5/verdict"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("QUARTER"))
                .andExpect(jsonPath("$.best.offerId").value(3))
                .andExpect(jsonPath("$.best.level").value("BELOW_USUAL"))
                .andExpect(jsonPath("$.best.confidence").value("REDUCED"))
                .andExpect(jsonPath("$.offers", hasSize(3)))
                .andExpect(jsonPath("$.offers[2].offerId").value(1))
                .andExpect(jsonPath("$.offers[2].level").value("ABOVE_USUAL"));
    }

    @Test
    void assessesDeclaredDiscounts() throws Exception {
        Category washers = new Category("WASHING_MACHINES", "Пральні машини");
        Product product = new Product(washers, "Пральна машина LG F2Y2HS5WE");
        product.setId(5L);
        when(catalog.getProduct(5L)).thenReturn(product);
        when(catalog.getOffersOfProduct(5L)).thenReturn(List.of(
                offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua")),
                offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua")),
                offer(3L, new Shop("EPICENTR", "Епіцентр", "https://epicentrk.ua"))));
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(
                1L, new PriceSnapshot(1L, new BigDecimal("20000"), new BigDecimal("26000"), true, SEEN),
                2L, snapshot(2L, "20000", true),
                3L, snapshot(3L, "20100", true)));

        mvc.perform(get("/api/products/5/discounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.offerId == 1)].level").value("FAKE"))
                .andExpect(jsonPath("$[?(@.offerId == 1)].declaredPercent").value(23))
                .andExpect(jsonPath("$[?(@.offerId == 1)].hits[0].code").value("PROMO_NOT_BELOW_MARKET"))
                .andExpect(jsonPath("$[?(@.offerId == 2)].level").value("NO_DISCOUNT"));
    }

    @Test
    void unknownProductGivesJson404() throws Exception {
        when(catalog.getProduct(99L)).thenThrow(NotFoundException.of("Product", 99L));

        mvc.perform(get("/api/products/99/history"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private static Offer offer(Long id, Shop shop) {
        Offer offer = new Offer(shop, "ext-" + id, "https://shop/" + id, "LG F2Y2HS5WE");
        offer.setId(id);
        offer.setLastSeenAt(SEEN);
        return offer;
    }

    private static PriceSnapshot snapshot(Long offerId, String price, boolean available) {
        return new PriceSnapshot(offerId, new BigDecimal(price), null, available, SEEN);
    }

    private static DailyPrice day(LocalDate date, String price, boolean available) {
        return new DailyPrice(date, new BigDecimal(price), null, available);
    }

}
