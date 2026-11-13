package org.viewer.pricemeter.web.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.matching.MatchingAlreadyRunningException;
import org.viewer.pricemeter.matching.MatchingReport;
import org.viewer.pricemeter.matching.MatchingService;
import org.viewer.pricemeter.matching.PendingReview;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchingPageController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityConfig.class)
class MatchingPageControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MatchingService matching;

    @MockitoBean
    private CatalogService catalog;

    @BeforeEach
    void setUp() {
        when(catalog.getEnabledShops()).thenReturn(List.of(
                new Shop("EPICENTR", "Епіцентр", "https://epicentrk.ua"),
                new Shop("ELDORADO", "Eldorado", "https://eldorado.ua")));
        when(catalog.getCategories()).thenReturn(List.of(new Category("WASHING_MACHINES", "Пральні машини")));
    }

    @Test
    void showsPendingOffersWithCandidates() throws Exception {
        when(matching.pendingReviews(MatchingPageController.MAX_SUGGESTIONS)).thenReturn(List.of(
                new PendingReview(7, "EPICENTR", "WASHING_MACHINES", "Пральна машина Samsung WW80T4040", "WW80T4040",
                        List.of(new PendingReview.Suggestion(42, 0.823, MatchScore.Reason.CODE_PREFIX, List.of(
                                new PendingReview.Member(3, "ELDORADO",
                                        "Пральна машина SAMSUNG WW80T4040CE1UA")))))));

        mvc.perform(get("/admin/matching"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Пральна машина Samsung WW80T4040"),
                        containsString("Пральні машини"),
                        containsString("82%"),
                        containsString("один код — початок іншого"),
                        containsString("Пральна машина SAMSUNG WW80T4040CE1UA"),
                        containsString("/admin/matching/offers/7/assign"),
                        containsString("value=\"42\""),
                        containsString("/admin/matching/offers/7/separate"))));
    }

    @Test
    void showsEmptyState() throws Exception {
        when(matching.pendingReviews(MatchingPageController.MAX_SUGGESTIONS)).thenReturn(List.of());

        mvc.perform(get("/admin/matching"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Перевіряти нічого")));
    }

    @Test
    void assignsOfferToProduct() throws Exception {
        mvc.perform(post("/admin/matching/offers/7/assign").with(csrf()).param("productId", "42"))
                .andExpect(redirectedUrl("/admin/matching"))
                .andExpect(flash().attribute("message", "Пропозицію #7 закріплено за товаром #42"));

        verify(matching).assignToProduct(7, 42);
    }

    @Test
    void keepsOfferSeparate() throws Exception {
        mvc.perform(post("/admin/matching/offers/7/separate").with(csrf()))
                .andExpect(redirectedUrl("/admin/matching"))
                .andExpect(flash().attribute("message", "Пропозиція #7 — окремий товар"));

        verify(matching).keepSeparate(7);
    }

    @Test
    void returnsToProductPageOrToOfferWhenProductIsGone() throws Exception {
        when(catalog.productExists(42)).thenReturn(true);
        mvc.perform(post("/admin/matching/offers/7/separate").param("product", "42").with(csrf()))
                .andExpect(redirectedUrl("/products/42"));

        // товар зник після перерахунку (його єдину пропозицію відкріпили)
        when(catalog.productExists(43)).thenReturn(false);
        mvc.perform(post("/admin/matching/offers/8/separate").param("product", "43").with(csrf()))
                .andExpect(redirectedUrl("/offers/8"));
    }

    @Test
    void resetsManualDecision() throws Exception {
        when(catalog.productExists(42)).thenReturn(true);

        mvc.perform(post("/admin/matching/offers/7/reset").param("product", "42").with(csrf()))
                .andExpect(redirectedUrl("/products/42"))
                .andExpect(flash().attribute("message",
                        "Ручне рішення щодо пропозиції #7 скасовано — зіставлено автоматично"));
        verify(matching).resetDecision(7);

        when(matching.resetDecision(8)).thenThrow(new IllegalArgumentException("Пропозиція #8 зіставлена автоматично"));
        mvc.perform(post("/admin/matching/offers/8/reset").with(csrf()))
                .andExpect(redirectedUrl("/admin/matching"))
                .andExpect(flash().attribute("error", "Пропозиція #8 зіставлена автоматично"));
    }

    @Test
    void reportsErrorsAsFlash() throws Exception {
        when(matching.assignToProduct(7, 99)).thenThrow(new IllegalArgumentException("Товар #99 з іншої категорії"));
        when(matching.matchAll()).thenThrow(new MatchingAlreadyRunningException());

        mvc.perform(post("/admin/matching/offers/7/assign").with(csrf()).param("productId", "99"))
                .andExpect(flash().attribute("error", "Товар #99 з іншої категорії"));
        mvc.perform(post("/admin/matching/run").with(csrf()))
                .andExpect(flash().attribute("error", "Зіставлення вже виконується — спробуйте за хвилину"));
    }

    @Test
    void runsMatchingOfAllCategories() throws Exception {
        when(matching.matchAll()).thenReturn(List.of(
                new MatchingReport("REFRIGERATORS", 9, 8, 1, 2, 0),
                new MatchingReport("WASHING_MACHINES", 20, 18, 2, 4, 1)));

        mvc.perform(post("/admin/matching/run").with(csrf()))
                .andExpect(redirectedUrl("/admin/matching"))
                .andExpect(flash().attribute("message",
                        "Зіставлено: 29 пропозицій → 26 товарів, з кількох магазинів — 3, на перевірку — 1"));
    }

}
