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
import org.viewer.pricemeter.matching.evaluation.LabelingProgress;
import org.viewer.pricemeter.matching.evaluation.LabelingService;
import org.viewer.pricemeter.matching.evaluation.LabelingTask;
import org.viewer.pricemeter.matching.evaluation.ScoreBand;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LabelingPageController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityConfig.class)
class LabelingPageControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private LabelingService labeling;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private PriceHistoryService priceHistory;

    @BeforeEach
    void setUp() {
        when(catalog.getEnabledShops()).thenReturn(List.of(
                new Shop("EPICENTR", "Епіцентр", "https://epicentrk.ua"),
                new Shop("ELDORADO", "Eldorado", "https://eldorado.ua")));
        when(catalog.getCategories()).thenReturn(List.of(new Category("WASHING_MACHINES", "Пральні машини")));
        when(catalog.getOffers(anyCollection())).thenReturn(List.of());
        when(priceHistory.getCurrentPrices(anyCollection())).thenReturn(Map.of());
        Map<ScoreBand, LabelingProgress.Band> bands = new EnumMap<>(ScoreBand.class);
        for (ScoreBand band : ScoreBand.values()) {
            bands.put(band, new LabelingProgress.Band(band == ScoreBand.REVIEW_HIGH ? 3 : 0,
                    band == ScoreBand.REVIEW_HIGH ? 2 : 0));
        }
        when(labeling.progress()).thenReturn(new LabelingProgress(3, 2, bands));
    }

    @Test
    void showsPairWithScoreAndProgress() throws Exception {
        when(labeling.nextTask()).thenReturn(Optional.of(new LabelingTask("WASHING_MACHINES",
                new LabelingTask.Side(7, "EPICENTR", "Пральна машина Samsung WW80T4040", "WW80T4040", "SAMSUNG"),
                new LabelingTask.Side(3, "ELDORADO", "Пральна машина SAMSUNG WW80T4040CE1UA", "WW80T4040CE1UA",
                        "SAMSUNG"),
                new MatchScore(0.823, MatchScore.Reason.CODE_PREFIX), ScoreBand.REVIEW_HIGH, 41)));

        mvc.perform(get("/admin/labeling"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Той самий товар?"),
                        containsString("Пральні машини"),
                        containsString("Пральна машина Samsung WW80T4040"),
                        containsString("WW80T4040CE1UA"),
                        containsString("82%"),
                        containsString("один код — початок іншого"),
                        containsString("немає в наявності"),
                        containsString("ще 41 нерозмічених пар"),
                        containsString("3 пар: той самий товар — 2, різні — 1"),
                        containsString("80–90%"),
                        containsString("name=\"offerA\" value=\"7\""),
                        containsString("name=\"offerB\" value=\"3\""))));
    }

    @Test
    void showsEmptyStateWhenNothingToLabel() throws Exception {
        when(labeling.nextTask()).thenReturn(Optional.empty());

        mvc.perform(get("/admin/labeling"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Розмічати нічого")));
    }

    @Test
    void savesLabelAndRedirects() throws Exception {
        mvc.perform(post("/admin/labeling").with(csrf()).param("offerA", "7").param("offerB", "3").param("same", "false"))
                .andExpect(redirectedUrl("/admin/labeling"))
                .andExpect(flash().attribute("message", "#7 і #3 — різні товари"));

        verify(labeling).label(7, 3, false);
    }

    @Test
    void reportsInvalidPair() throws Exception {
        when(labeling.label(7, 8, true)).thenThrow(new IllegalArgumentException("Пропозиції одного магазину не розмічаються"));

        mvc.perform(post("/admin/labeling").with(csrf()).param("offerA", "7").param("offerB", "8").param("same", "true"))
                .andExpect(redirectedUrl("/admin/labeling"))
                .andExpect(flash().attribute("error", "Пропозиції одного магазину не розмічаються"));
    }

}
