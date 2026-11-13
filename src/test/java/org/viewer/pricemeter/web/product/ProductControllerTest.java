package org.viewer.pricemeter.web.product;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.ProductFilter;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.subscription.service.SubscriptionService;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, TimeConfig.class, ProductMarketService.class, VerdictService.class, PriceAnalyticsService.class, FakeDiscountDetector.class,
        PromoNotBelowMarketRule.class, InflatedOldPriceRule.class, SamePriceElsewhereRule.class})
class ProductControllerTest {

    private static final Instant SEEN = Instant.parse("2026-10-10T01:15:00Z");

    private final Category washers = new Category("WASHING_MACHINES", "Пральні машини");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private PriceHistoryService priceHistory;

    @MockitoBean
    private SubscriptionService subscriptions;

    @Test
    void comparesShopsAgainstMarketMedian() throws Exception {
        Product product = product(5L, "Пральна машина LG F2Y2HS5WE");
        Offer moyo = offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"), "Пральна машина LG F2Y2HS5WE");
        Offer citrus = offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua"), "LG F2Y2HS5WE");
        Offer epicentr = offer(3L, new Shop("EPICENTR", "Епіцентр", "https://epicentrk.ua"), "LG F2Y2HS5WE біла");
        Offer eldorado = offer(4L, new Shop("ELDORADO", "Eldorado", "https://eldorado.ua"), "LG F2Y2HS5WE (немає)");
        when(catalog.getProduct(5L)).thenReturn(product);
        when(catalog.getOffersOfProduct(5L)).thenReturn(List.of(moyo, citrus, epicentr, eldorado));
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(
                1L, snapshot(1L, "18999", null, true),
                2L, snapshot(2L, "16999", "19999", true),
                3L, snapshot(3L, "15499", null, true),
                4L, snapshot(4L, "14999", null, false)));

        String html = mvc.perform(get("/products/5"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/details"))
                .andExpect(content().string(allOf(
                        containsString("Пральна машина LG F2Y2HS5WE"),
                        containsString("код F2Y2HS5WE"),
                        // медіана наявних 15 499 / 16 999 / 18 999
                        containsString("16 999 ₴"),
                        containsString("3 з 4"),
                        containsString("+12%"),
                        containsString("−9%"),
                        containsString("найдешевше"),
                        containsString("href=\"/offers/4\""),
                        containsString("data-product-id=\"5\""),
                        // історії немає — вердикт лише за ринком (медіана інших магазинів)
                        containsString("Найнижча ціна — Епіцентр"),
                        containsString("Ціна нижча за звичайну"),
                        containsString("На 14% дешевше за медіану інших магазинів (2)"),
                        containsString("довіра знижена"),
                        containsString("завищена"),
                        // Цитрус: «−15%» від 19 999, але акційна ціна на рівні ринку, а стара — на 16% вища за нього
                        containsString("фіктивна знижка"),
                        containsString("Заявлена стара ціна на 16% вища за медіану інших магазинів"),
                        containsString("звичайна"),
                        containsString("/js/product-chart.js"))))
                .andReturn().getResponse().getContentAsString();

        // наявні — від найдешевшої, без наявності — у кінці
        assertThat(html.indexOf("/offers/3")).isLessThan(html.indexOf("/offers/2"));
        assertThat(html.indexOf("/offers/2")).isLessThan(html.indexOf("/offers/1"));
        assertThat(html.indexOf("/offers/1")).isLessThan(html.indexOf("/offers/4"));
    }

    @Test
    void adminSeesMatchingDecisionsWithActions() throws Exception {
        when(catalog.getProduct(6L)).thenReturn(product(6L, "Холодильник Gorenje RF414EPS4"));
        Offer moyo = offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"), "Gorenje RF414EPS4");
        moyo.setMatchStatus(MatchStatus.MANUAL);
        Offer citrus = offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua"), "Gorenje RF414EPS4 білий");
        citrus.setMatchStatus(MatchStatus.AUTO);
        citrus.setMatchConfidence(new BigDecimal("0.950"));
        when(catalog.getOffersOfProduct(6L)).thenReturn(List.of(moyo, citrus));

        mvc.perform(get("/products/6").with(user("admin@pricemeter.local").roles("ADMIN")))
                .andExpect(content().string(allOf(
                        containsString("видно лише адміністратору"),
                        containsString("вручну — той самий"),
                        containsString("0.95"),
                        // Moyo вже закріплено: лише «відкріпити» й «скинути»; Цитрус — «закріпити» й «відкріпити»
                        containsString("/admin/matching/offers/1/reset"),
                        not(containsString("/admin/matching/offers/1/assign")),
                        containsString("/admin/matching/offers/2/assign"),
                        containsString("/admin/matching/offers/2/separate"),
                        not(containsString("/admin/matching/offers/2/reset")))));

        mvc.perform(get("/products/6").with(user("buyer@example.com")))
                .andExpect(content().string(not(containsString("видно лише адміністратору"))));
    }

    @Test
    void followCardDependsOnUserAndSubscription() throws Exception {
        when(catalog.getProduct(6L)).thenReturn(product(6L, "Холодильник Gorenje RF414EPS4"));
        when(catalog.getOffersOfProduct(6L)).thenReturn(List.of());

        // гість — посилання на вхід
        mvc.perform(get("/products/6"))
                .andExpect(content().string(containsString("href=\"/account/follow/6\"")));

        // підписаний користувач — бажана ціна в полі й «Не стежити»
        when(subscriptions.find("buyer@example.com", 6L))
                .thenReturn(Optional.of(new SubscriptionService.SubscriptionStatus(new BigDecimal("15000.00"))));
        mvc.perform(get("/products/6").with(user("buyer@example.com")))
                .andExpect(content().string(allOf(
                        containsString("Ви стежите за ціною"),
                        containsString("value=\"15000\""),
                        containsString("/account/subscriptions/6/delete"),
                        not(containsString("/account/follow/6")))));
    }

    @Test
    void singleShopHasNoMarketComparison() throws Exception {
        when(catalog.getProduct(6L)).thenReturn(product(6L, "Холодильник Gorenje RF414EPS4"));
        when(catalog.getOffersOfProduct(6L)).thenReturn(List.of(
                offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"), "Холодильник Gorenje RF414EPS4")));
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(1L, snapshot(1L, "20999", null, true)));

        mvc.perform(get("/products/6"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("20 999 ₴"),
                        not(containsString("найдешевше")),
                        not(containsString("відхилення — від медіани")))));
    }

    @Test
    void listsProductsWithShopsAndLowestPrice() throws Exception {
        Product lg = product(5L, "Пральна машина LG F2Y2HS5WE");
        Product gorenje = product(6L, "Холодильник Gorenje RF414EPS4");
        gorenje.setModelCode(null);
        Offer moyo = offer(1L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"), "LG F2Y2HS5WE");
        Offer citrus = offer(2L, new Shop("CITRUS", "Цитрус", "https://citrus.ua"), "LG F2Y2HS5WE");
        Offer gorenjeOffer = offer(3L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"), "Gorenje RF414EPS4");
        moyo.setProduct(lg);
        citrus.setProduct(lg);
        gorenjeOffer.setProduct(gorenje);
        when(catalog.searchProducts(any(), any())).thenReturn(new PageImpl<>(List.of(lg, gorenje)));
        when(catalog.getOffersOfProducts(List.of(5L, 6L))).thenReturn(List.of(moyo, citrus, gorenjeOffer));
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(
                1L, snapshot(1L, "20799", null, true),
                2L, snapshot(2L, "20499", null, true),
                3L, snapshot(3L, "18999", null, false)));

        mvc.perform(get("/products").param("q", "lg").param("category", "WASHING_MACHINES").param("multi", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("products/list"))
                .andExpect(content().string(allOf(
                        containsString("href=\"/products/5\""),
                        containsString("Цитрус"),
                        containsString("20 499"),
                        containsString("у 2 магазинах"),
                        containsString("немає в наявності"),
                        containsString("checked"))));

        ArgumentCaptor<ProductFilter> filter = ArgumentCaptor.forClass(ProductFilter.class);
        verify(catalog).searchProducts(filter.capture(), any());
        assertThat(filter.getValue()).isEqualTo(new ProductFilter("WASHING_MACHINES", "lg", true));
    }

    @Test
    void unknownProductIsNotFound() throws Exception {
        when(catalog.getProduct(99L)).thenThrow(NotFoundException.of("Product", 99L));

        mvc.perform(get("/products/99")).andExpect(status().isNotFound());
    }

    private Product product(Long id, String title) {
        Product product = new Product(washers, title);
        product.setId(id);
        product.setBrand("LG");
        product.setModelCode("F2Y2HS5WE");
        return product;
    }

    private Offer offer(Long id, Shop shop, String title) {
        Offer offer = new Offer(shop, "ext-" + id, "https://shop/" + id, title);
        offer.setId(id);
        offer.setCategory(washers);
        offer.setLastSeenAt(SEEN);
        return offer;
    }

    private static PriceSnapshot snapshot(Long offerId, String price, String oldPrice, boolean available) {
        return new PriceSnapshot(offerId, new BigDecimal(price), oldPrice != null ? new BigDecimal(oldPrice) : null,
                available, SEEN);
    }

}
