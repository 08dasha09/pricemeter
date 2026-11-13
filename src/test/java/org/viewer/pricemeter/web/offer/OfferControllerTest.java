package org.viewer.pricemeter.web.offer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import org.viewer.pricemeter.catalog.service.OfferFilter;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(OfferController.class)
@Import({SecurityConfig.class, TimeConfig.class, ProductMarketService.class, VerdictService.class, PriceAnalyticsService.class, FakeDiscountDetector.class,
        PromoNotBelowMarketRule.class, InflatedOldPriceRule.class, SamePriceElsewhereRule.class})
class OfferControllerTest {

    private static final Instant SEEN = Instant.parse("2026-10-04T09:30:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private PriceHistoryService priceHistory;

    @Test
    void rendersOffersWithCurrentPrices() throws Exception {
        Shop moyo = new Shop("MOYO", "Moyo", "https://www.moyo.ua");
        Category washers = new Category("WASHING_MACHINES", "Пральні машини");
        Offer discounted = offer(1L, moyo, washers, "Samsung WW80T4040CE1UA");
        Offer noHistory = offer(2L, moyo, washers, "Bosch WAN28263UA");
        when(catalog.searchOffers(any(), any())).thenAnswer(inv -> new PageImpl<>(
                List.of(discounted, noHistory), inv.getArgument(1, Pageable.class), 2));
        when(catalog.getEnabledShops()).thenReturn(List.of(moyo));
        when(catalog.getCategories()).thenReturn(List.of(washers));
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of(1L,
                new PriceSnapshot(1L, new BigDecimal("15999.00"), new BigDecimal("18999.00"), true, SEEN)));

        mvc.perform(get("/offers"))
                .andExpect(status().isOk())
                .andExpect(view().name("offers/list"))
                .andExpect(content().string(allOf(
                        containsString("Samsung WW80T4040CE1UA"),
                        containsString("href=\"/offers/1\""),
                        containsString("15 999"),
                        containsString("18 999"),
                        containsString("−16%"),
                        containsString("Bosch WAN28263UA"),
                        // 09:30 UTC = 12:30 за Києвом
                        containsString("04.10.2026 12:30"),
                        containsString("Знайдено: 2"))));
    }

    @Test
    void passesFiltersAndKeepsThemInPagination() throws Exception {
        Shop citrus = new Shop("CITRUS", "Цитрус", "https://citrus.ua");
        List<Offer> content = List.of(offer(1L, citrus, null, "Bosch WAN28263UA"));
        when(catalog.searchOffers(any(), any())).thenAnswer(inv -> new PageImpl<>(
                content, inv.getArgument(1, Pageable.class), 120));
        when(catalog.getEnabledShops()).thenReturn(List.of(citrus));

        mvc.perform(get("/offers").param("shop", "CITRUS").param("q", "bosch wan").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Сторінка 2 з 3"),
                        containsString("href=\"/offers?shop=CITRUS&amp;q=bosch%20wan&amp;page=0\""),
                        containsString("href=\"/offers?shop=CITRUS&amp;q=bosch%20wan&amp;page=2\""),
                        not(containsString("неактивна")))));

        verify(catalog).searchOffers(eq(new OfferFilter("CITRUS", null, "bosch wan", true)),
                eq(PageRequest.of(1, OfferController.PAGE_SIZE,
                        Sort.by(Sort.Order.desc("lastSeenAt"), Sort.Order.desc("id")))));
    }

    @Test
    void rendersOfferDetailsWithPriceChangesNewestFirst() throws Exception {
        Shop moyo = new Shop("MOYO", "Moyo", "https://www.moyo.ua");
        Offer offer = offer(7L, moyo, new Category("WASHING_MACHINES", "Пральні машини"), "Samsung WW80T4040CE1UA");
        offer.setMpn("WW80T4040CE1UA");
        offer.setFirstSeenAt(SEEN);
        PriceSnapshot first = new PriceSnapshot(7L, new BigDecimal("17999"), null, true, SEEN);
        PriceSnapshot latest = new PriceSnapshot(7L, new BigDecimal("15999"), new BigDecimal("18999"), true,
                SEEN.plusSeconds(86_400));
        when(catalog.getOffer(7L)).thenReturn(offer);
        when(priceHistory.getAll(7L)).thenReturn(List.of(first, latest));
        when(priceHistory.getCurrentPrices(List.of(7L))).thenReturn(Map.of(7L, latest));

        String html = mvc.perform(get("/offers/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("offers/details"))
                .andExpect(content().string(allOf(
                        containsString("Samsung WW80T4040CE1UA"),
                        containsString("WW80T4040CE1UA"),
                        containsString("15 999 ₴"),
                        containsString("−16%"),
                        containsString("href=\"https://shop/7\""),
                        containsString("href=\"/offers?shop=MOYO\""),
                        containsString("data-offer-id=\"7\""),
                        containsString("data-period=\"MONTH\""),
                        containsString("05.10.2026 12:30"),
                        // мінімум, максимум і тривалість спостереження за історією
                        containsString("17 999 ₴"),
                        containsString("2 дн."),
                        // без товару й без історії за квартал оцінки немає
                        containsString("Оцінити поки не можна"),
                        // знижку заявлено, але порівняти нема з чим
                        containsString("Знижку не перевірити"))))
                .andReturn().getResponse().getContentAsString();

        // у таблиці змін новіший стан — першим
        String changes = html.substring(html.indexOf("Зміни ціни"));
        assertThat(changes.indexOf("15 999")).isPositive().isLessThan(changes.indexOf("17 999"));
    }

    @Test
    void linksMatchedOfferToItsProduct() throws Exception {
        Category washers = new Category("WASHING_MACHINES", "Пральні машини");
        Offer matched = offer(8L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"), washers, "LG F2Y2HS5WE");
        matched.setFirstSeenAt(SEEN);
        Product product = new Product(washers, "Пральна машина LG F2Y2HS5WE");
        product.setId(42L);
        matched.setProduct(product);
        when(catalog.getOffer(8L)).thenReturn(matched);
        when(priceHistory.getAll(8L)).thenReturn(List.of());

        mvc.perform(get("/offers/8"))
                .andExpect(content().string(containsString("href=\"/products/42\"")));

        Offer unmatched = offer(9L, new Shop("MOYO", "Moyo", "https://www.moyo.ua"), washers, "LG F2Y2HS6WE");
        unmatched.setFirstSeenAt(SEEN);
        when(catalog.getOffer(9L)).thenReturn(unmatched);
        when(priceHistory.getAll(9L)).thenReturn(List.of());

        mvc.perform(get("/offers/9"))
                .andExpect(content().string(not(containsString("/products/"))));
    }

    @Test
    void unknownOfferGives404Page() throws Exception {
        when(catalog.getOffer(99L)).thenThrow(NotFoundException.of("Offer", 99L));

        mvc.perform(get("/offers/99"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
        mvc.perform(get("/offers/abc"))
                .andExpect(status().isNotFound());
    }

    private static Offer offer(Long id, Shop shop, Category category, String title) {
        Offer offer = new Offer(shop, "ext-" + id, "https://shop/" + id, title);
        offer.setId(id);
        offer.setCategory(category);
        offer.setLastSeenAt(SEEN);
        return offer;
    }

}
