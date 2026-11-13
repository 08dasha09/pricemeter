package org.viewer.pricemeter.web.shop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viewer.pricemeter.analytics.ShopPriceIndexService;
import org.viewer.pricemeter.analytics.ShopPriceIndexService.PriceIndexReport;
import org.viewer.pricemeter.analytics.ShopPriceIndexService.ShopPriceIndex;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.ProductFilter;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.crawler.scheduling.CrawlHealthService;
import org.viewer.pricemeter.fakediscount.DiscountReportService;
import org.viewer.pricemeter.fakediscount.ShopDiscountStats;
import org.viewer.pricemeter.web.category.CategoryController;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ShopController.class, CategoryController.class})
@Import(SecurityConfig.class)
class ShopAndCategoryPagesTest {

    private static final Category WASHERS = new Category("WASHING_MACHINES", "Пральні машини");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private ShopPriceIndexService priceIndex;

    @MockitoBean
    private DiscountReportService discounts;

    @MockitoBean
    private CrawlHealthService crawlHealth;

    @BeforeEach
    void setUp() {
        when(catalog.getCategories()).thenReturn(List.of(WASHERS));
        ShopPriceIndex moyo = new ShopPriceIndex("MOYO", "Moyo", 120, -3, 48, 30);
        ShopPriceIndex citrus = new ShopPriceIndex("CITRUS", "Цитрус", 100, 4, 20, 30);
        when(priceIndex.report()).thenReturn(new PriceIndexReport(List.of(moyo, citrus),
                Map.of("WASHING_MACHINES", List.of(moyo, citrus))));
        when(discounts.findings()).thenReturn(List.of());
        when(discounts.shopStats(anyList())).thenReturn(List.of(
                new ShopDiscountStats("MOYO", "Moyo", 66, 40, 3, 20, 17)));
    }

    @Test
    void shopPageShowsPriceIndexDiscountsAndCrawl() throws Exception {
        when(catalog.getShop("MOYO")).thenReturn(new Shop("MOYO", "Moyo", "https://www.moyo.ua"));
        when(catalog.countActiveOffers("MOYO")).thenReturn(901L);
        when(crawlHealth.shops()).thenReturn(List.of(new CrawlHealthService.ShopCrawlHealth("MOYO",
                Instant.parse("2026-10-19T07:42:00Z"), 5, 0, 1, 0)));

        mvc.perform(get("/shops/moyo"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("901 пропозицій на сайті"),
                        containsString("Зазвичай на 3% дешевше"),
                        containsString("Найдешевший сам — на 48 з них (40%), нарівні з іншими найдешевшими — на 30 (25%)"),
                        containsString("href=\"/categories/washing_machines\""),
                        containsString("93%"),
                        containsString("Останній обхід — 19.10.2026 10:42"))));
    }

    @Test
    void unknownShopIsNotFound() throws Exception {
        when(catalog.getShop("NOPE")).thenThrow(NotFoundException.of("Shop", "NOPE"));

        mvc.perform(get("/shops/nope")).andExpect(status().isNotFound());
    }

    @Test
    void categoryPageComparesShops() throws Exception {
        when(catalog.getCategory("WASHING_MACHINES")).thenReturn(WASHERS);
        when(catalog.searchProducts(eq(new ProductFilter("WASHING_MACHINES", null, false)), any()))
                .thenReturn(new PageImpl<>(Collections.<Product>emptyList(), PageRequest.of(0, 1), 412));
        when(catalog.searchProducts(eq(new ProductFilter("WASHING_MACHINES", null, true)), any()))
                .thenReturn(new PageImpl<>(Collections.<Product>emptyList(), PageRequest.of(0, 1), 120));

        mvc.perform(get("/categories/washing_machines"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("412 товарів, з них у кількох магазинах — 120"),
                        containsString("href=\"/shops/moyo\""),
                        containsString("−3%"),
                        containsString("+4%"),
                        containsString("Знижки в категорії"),
                        containsString("href=\"/products?category=WASHING_MACHINES\""))));
    }

}
