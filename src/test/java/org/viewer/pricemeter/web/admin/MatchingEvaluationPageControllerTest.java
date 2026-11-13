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
import org.viewer.pricemeter.matching.evaluation.LabelingTask;
import org.viewer.pricemeter.matching.evaluation.MatchingEvaluation;
import org.viewer.pricemeter.matching.evaluation.MatchingEvaluator;
import org.viewer.pricemeter.matching.evaluation.Metrics;
import org.viewer.pricemeter.matching.evaluation.ScoreBand;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchingEvaluationPageController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityConfig.class)
class MatchingEvaluationPageControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MatchingEvaluator evaluator;

    @MockitoBean
    private CatalogService catalog;

    @BeforeEach
    void setUp() {
        when(catalog.getEnabledShops()).thenReturn(List.of(
                new Shop("MOYO", "Moyo", "https://moyo.ua"),
                new Shop("EPICENTR", "Епіцентр", "https://epicentrk.ua")));
        when(catalog.getCategories()).thenReturn(List.of(new Category("REFRIGERATORS", "Холодильники")));
    }

    @Test
    void showsMetricsThresholdsAndErrors() throws Exception {
        Metrics sample = new Metrics(40, 2, 4, 74);
        Metrics weighted = new Metrics(400, 5, 30, 9000);
        Map<ScoreBand, Long> population = new EnumMap<>(ScoreBand.class);
        for (ScoreBand band : ScoreBand.values()) {
            population.put(band, band == ScoreBand.VERY_LOW ? 51234L : 10L);
        }
        MatchingEvaluation evaluation = new MatchingEvaluation(120, 44, 1,
                new MatchingEvaluation.Estimate(sample, weighted),
                List.of(new MatchingEvaluation.ThresholdPoint(0.65, new MatchingEvaluation.Estimate(sample, weighted)),
                        new MatchingEvaluation.ThresholdPoint(0.9, new MatchingEvaluation.Estimate(sample, weighted))),
                population, 0.9, 0.65, 0.98, 0.98, 0.85, null,
                List.of(new MatchingEvaluation.PairOutcome("REFRIGERATORS",
                        new LabelingTask.Side(1, "MOYO", "Холодильник Bosch KGN39VLEB", "KGN39VLEB", "BOSCH"),
                        new LabelingTask.Side(2, "EPICENTR", "Холодильник Bosch KGN39VLEC", "KGN39VLEC", "BOSCH"),
                        false, true, new MatchScore(0.91, MatchScore.Reason.BASE_CODE))));
        when(evaluator.evaluate()).thenReturn(evaluation);

        mvc.perform(get("/admin/matching/evaluation"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("Якість зіставлення"),
                        containsString("98.8%"),           // зважена точність 400 / 405
                        containsString("95.2%"),           // точність на вибірці 40 / 42
                        containsString("1 міток не враховано"),
                        containsString("0.85"),
                        containsString("· авто"),
                        containsString("· перевірка"),
                        containsString("40 / 2 / 4"),
                        containsString("Холодильник Bosch KGN39VLEC"),
                        containsString("хибне об&#39;єднання"),
                        containsString("код відрізняється лише регіоном"),
                        containsString("51234"))));
    }

    @Test
    void showsHintWithoutLabels() throws Exception {
        when(evaluator.evaluate()).thenReturn(new MatchingEvaluation(0, 0, 0,
                new MatchingEvaluation.Estimate(Metrics.EMPTY, Metrics.EMPTY), List.of(), Map.of(), 0.9, 0.65, 0.98,
                0.98, null, null, List.of()));

        mvc.perform(get("/admin/matching/evaluation"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Розмічених пар ще немає")));
    }

    @Test
    void formatsPercentages() {
        assertThat(MatchingEvaluationPageController.percent(null)).isEqualTo("—");
        assertThat(MatchingEvaluationPageController.percent(0.98765)).isEqualTo("98.8%");
        assertThat(MatchingEvaluationPageController.percent(1.0)).isEqualTo("100.0%");
    }

}
