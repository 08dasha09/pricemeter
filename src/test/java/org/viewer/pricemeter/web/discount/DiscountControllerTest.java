package org.viewer.pricemeter.web.discount;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viewer.pricemeter.analytics.MarketOffer;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.fakediscount.DiscountFinding;
import org.viewer.pricemeter.fakediscount.DiscountReportService;
import org.viewer.pricemeter.fakediscount.ShopDiscountStats;
import org.viewer.pricemeter.fakediscount.rule.RuleHit;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(DiscountController.class)
@Import(SecurityConfig.class)
class DiscountControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DiscountReportService report;

    @MockitoBean
    private CatalogService catalog;

    private final DiscountFinding fake = finding(1, "MOYO", "Пральна машина LG F2Y2HS5WE", 7L, "WASHING_MACHINES",
            new DiscountAssessment(DiscountAssessment.Level.FAKE, 23, 0, new BigDecimal("20000"), 1.2, List.of(
                    new RuleHit("PROMO_NOT_BELOW_MARKET", 0.5, "Акційна ціна на рівні інших магазинів"),
                    new RuleHit("INFLATED_OLD_PRICE", 0.4, "Заявлена стара ціна на 30% вища за медіану"))));
    private final DiscountFinding genuine = finding(2, "CITRUS", "Холодильник Gorenje RK4182PS4", null,
            "REFRIGERATORS",
            new DiscountAssessment(DiscountAssessment.Level.GENUINE, 10, -18, new BigDecimal("18250"), 0, List.of()));

    @BeforeEach
    void setUp() {
        when(report.findings()).thenReturn(List.of(fake, genuine));
        when(report.shopStats(any())).thenReturn(List.of(
                new ShopDiscountStats("MOYO", "Moyo", 5, 4, 1, 1, 2),
                new ShopDiscountStats("CITRUS", "Цитрус", 3, 0, 0, 0, 0)));
        when(catalog.getEnabledShops()).thenReturn(List.of(new Shop("MOYO", "Moyo", "https://www.moyo.ua")));
        when(catalog.getCategories()).thenReturn(List.of(
                new Category("WASHING_MACHINES", "Пральні машини"), new Category("REFRIGERATORS", "Холодильники")));
    }

    @Test
    void showsUntrustworthyDiscountsAndShopShares() throws Exception {
        mvc.perform(get("/discounts"))
                .andExpect(status().isOk())
                .andExpect(view().name("discounts/list"))
                .andExpect(content().string(allOf(
                        // Moyo: (1 + 2) / 4 = 75%
                        containsString("75%"),
                        containsString("Знайдено: 1"),
                        containsString("фіктивна знижка"),
                        containsString("Заявлена стара ціна на 30% вища за медіану"),
                        containsString("href=\"/products/7\""),
                        containsString("Пральні машини"),
                        not(containsString("Холодильник Gorenje RK4182PS4")))));
    }

    @Test
    void filtersByLevelShopAndCategory() throws Exception {
        mvc.perform(get("/discounts").param("level", "GENUINE"))
                .andExpect(content().string(allOf(
                        containsString("Холодильник Gorenje RK4182PS4"),
                        // не зіставлена — посилання на пропозицію
                        containsString("href=\"/offers/2\""),
                        containsString("знижка справжня"))));
        mvc.perform(get("/discounts").param("level", "ALL").param("shop", "CITRUS"))
                .andExpect(content().string(allOf(
                        containsString("Знайдено: 1"), containsString("Холодильник Gorenje RK4182PS4"))));
        mvc.perform(get("/discounts").param("level", "ALL").param("category", "WASHING_MACHINES"))
                .andExpect(content().string(allOf(
                        containsString("Знайдено: 1"), containsString("Пральна машина LG F2Y2HS5WE"))));
    }

    private static DiscountFinding finding(long id, String shop, String title, Long productId, String category,
                                           DiscountAssessment assessment) {
        MarketOffer offer = new MarketOffer(id, shop, shop.equals("MOYO") ? "Moyo" : "Цитрус", title,
                "https://shop/" + id, new BigDecimal("20000"), new BigDecimal("26000"), true, true,
                LocalDateTime.of(2026, 10, 11, 4, 0), null, false);
        return new DiscountFinding(offer, productId, category, assessment);
    }

}
