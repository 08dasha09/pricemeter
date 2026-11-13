package org.viewer.pricemeter.web;

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
import org.viewer.pricemeter.crawler.scheduling.CrawlJournal;
import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.fakediscount.DiscountFinding;
import org.viewer.pricemeter.fakediscount.DiscountReportService;
import org.viewer.pricemeter.fakediscount.rule.RuleHit;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(HomeController.class)
@Import(SecurityConfig.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private DiscountReportService discounts;

    @MockitoBean
    private CrawlJournal journal;

    @Test
    void showsSearchOverviewAndDiscountPicks() throws Exception {
        when(catalog.getCategories()).thenReturn(List.of(new Category("WASHING_MACHINES", "Пральні машини")));
        when(catalog.getEnabledShops()).thenReturn(List.of(new Shop("MOYO", "Moyo", "https://www.moyo.ua")));
        when(catalog.overview()).thenReturn(new CatalogService.CatalogOverview(1790, 519, 2514));
        when(journal.lastFinishedStart()).thenReturn(Optional.of(Instant.parse("2026-10-19T07:42:00Z")));
        when(discounts.findings()).thenReturn(List.of(
                finding(1, "Пральна машина LG F2Y2HS5WE", 5L, fake()),
                finding(2, "Пральна машина Samsung WW80T4040CE1UA", 6L, genuine(-12)),
                finding(3, "Холодильник Gorenje", null, unknown())));

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(allOf(
                        containsString("Чи справді це знижка?"),
                        containsString("action=\"/products\""),
                        containsString("href=\"/categories/washing_machines\""),
                        containsString("href=\"/shops/moyo\""),
                        containsString("1 790"),
                        // перевірні — без «не перевірити»: 1 недостовірна з 2
                        containsString("1 з 2"),
                        containsString("19.10 10:42"),
                        containsString("Реальні знижки сьогодні"),
                        containsString("Акційна ціна на 12% нижча за медіану інших магазинів"),
                        containsString("href=\"/products/6\""),
                        containsString("фіктивна знижка"),
                        containsString("Акційна ціна на рівні інших магазинів"))));
    }

    @Test
    void emptyCatalogStillRenders() throws Exception {
        when(catalog.getCategories()).thenReturn(List.of());
        when(catalog.overview()).thenReturn(new CatalogService.CatalogOverview(0, 0, 0));
        when(journal.lastFinishedStart()).thenReturn(Optional.empty());
        when(discounts.findings()).thenReturn(List.of());

        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Сьогодні таких немає"), containsString("Підозрілих знижок не знайдено"))));
    }

    @Test
    void realDealsAreSortedBySavingAndLimited() {
        List<DiscountFinding> findings = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            findings.add(finding(i, "Товар " + i, (long) i, genuine(-5 - i)));
        }

        assertThat(HomeController.realDeals(findings))
                .hasSize(HomeController.PICKS)
                .extracting(f -> f.offer().id())
                .containsExactly(8L, 7L, 6L, 5L, 4L, 3L);
    }

    private static DiscountAssessment fake() {
        return new DiscountAssessment(DiscountAssessment.Level.FAKE, 23, 0, new BigDecimal("20000"), 0.9, List.of(
                new RuleHit("PROMO_NOT_BELOW_MARKET", 0.5, "Акційна ціна на рівні інших магазинів"),
                new RuleHit("INFLATED_OLD_PRICE", 0.4, "Заявлена стара ціна на 30% вища за медіану")));
    }

    private static DiscountAssessment genuine(int deviation) {
        return new DiscountAssessment(DiscountAssessment.Level.GENUINE, 23, deviation, new BigDecimal("23000"), 0,
                List.of());
    }

    private static DiscountAssessment unknown() {
        return new DiscountAssessment(DiscountAssessment.Level.UNKNOWN, 23, null, null, 0, List.of());
    }

    private static DiscountFinding finding(long id, String title, Long productId, DiscountAssessment assessment) {
        MarketOffer offer = new MarketOffer(id, "MOYO", "Moyo", title, "https://shop/" + id,
                new BigDecimal("20000"), new BigDecimal("26000"), true, true,
                LocalDateTime.of(2026, 10, 19, 4, 0), null, false);
        return new DiscountFinding(offer, productId, "WASHING_MACHINES", assessment);
    }

}
