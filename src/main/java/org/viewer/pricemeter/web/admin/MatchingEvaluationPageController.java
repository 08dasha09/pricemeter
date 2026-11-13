package org.viewer.pricemeter.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.matching.evaluation.MatchingEvaluation;
import org.viewer.pricemeter.matching.evaluation.MatchingEvaluator;
import org.viewer.pricemeter.matching.evaluation.Metrics;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Якість зіставлення на розміченій вибірці: Precision / Recall / F1 поточних рішень і за порогами.
 */
@Controller
@RequestMapping("/admin/matching/evaluation")
@RequiredArgsConstructor
public class MatchingEvaluationPageController {

    private final MatchingEvaluator evaluator;
    private final CatalogService catalog;

    /** Метрики, підготовлені до показу: відсотки з одним знаком, {@code —} — не визначено. */
    public record MetricsView(String precision, String recall, String f1, long tp, long fp, long fn) {

        static MetricsView of(Metrics m) {
            return new MetricsView(percent(m.precision()), percent(m.recall()), percent(m.f1()),
                    Math.round(m.tp()), Math.round(m.fp()), Math.round(m.fn()));
        }

    }

    /** Рядок кривої порогів: зважені метрики й кількості на вибірці. */
    public record ThresholdRow(double threshold, MetricsView weighted, MetricsView sample, boolean auto,
                               boolean review) {
    }

    @GetMapping
    public String page(Model model) {
        MatchingEvaluation evaluation = evaluator.evaluate();
        model.addAttribute("evaluation", evaluation);
        model.addAttribute("systemWeighted", MetricsView.of(evaluation.system().weighted()));
        model.addAttribute("systemSample", MetricsView.of(evaluation.system().sample()));
        model.addAttribute("rows", evaluation.curve().stream()
                .map(p -> new ThresholdRow(p.threshold(), MetricsView.of(p.estimate().weighted()),
                        MetricsView.of(p.estimate().sample()),
                        sameThreshold(p.threshold(), evaluation.autoThreshold()),
                        sameThreshold(p.threshold(), evaluation.reviewThreshold())))
                .toList());
        model.addAttribute("shopNames", catalog.getEnabledShops().stream()
                .collect(Collectors.toMap(Shop::getCode, Shop::getName)));
        model.addAttribute("categoryNames", catalog.getCategories().stream()
                .collect(Collectors.toMap(Category::getCode, Category::getName)));
        model.addAttribute("reasons", MatchingPageController.REASONS);
        return "admin/evaluation";
    }

    static String percent(Double value) {
        return value == null ? "—" : String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private static boolean sameThreshold(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

}
