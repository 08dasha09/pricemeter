package org.viewer.pricemeter.web.discount;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.fakediscount.DiscountFinding;
import org.viewer.pricemeter.fakediscount.DiscountReportService;
import org.viewer.pricemeter.web.DiscountView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сторінка «Знижки»: усі поточні заявлені знижки з оцінкою достовірності й частка недостовірних по магазинах.
 */
@Controller
@RequestMapping("/discounts")
@RequiredArgsConstructor
public class DiscountController {

    static final int PAGE_SIZE = 50;

    /** Фільтр за оцінкою: значення параметра → підпис. */
    static final Map<String, String> LEVELS = new LinkedHashMap<>();

    static {
        LEVELS.put("ALL", "Усі");
        LEVELS.put("UNTRUSTWORTHY", "Фіктивні та підозрілі");
        LEVELS.put("FAKE", "Фіктивні");
        LEVELS.put("SUSPICIOUS", "Підозрілі");
        LEVELS.put("GENUINE", "Справжні");
        LEVELS.put("UNKNOWN", "Не перевірити");
    }

    private final DiscountReportService report;
    private final CatalogService catalog;

    /** Рядок списку. */
    public record Row(DiscountFinding finding, DiscountView view, String categoryName) {
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "UNTRUSTWORTHY") String level,
                       @RequestParam(required = false) String shop,
                       @RequestParam(required = false) String category,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        List<DiscountFinding> all = report.findings();
        Map<String, String> categoryNames = catalog.getCategories().stream()
                .collect(Collectors.toMap(Category::getCode, Category::getName));

        List<DiscountFinding> filtered = all.stream()
                .filter(f -> matchesLevel(f, level))
                .filter(f -> shop == null || shop.isBlank() || f.offer().shopCode().equalsIgnoreCase(shop))
                .filter(f -> category == null || category.isBlank() || category.equalsIgnoreCase(f.categoryCode()))
                .toList();
        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.min(Math.max(page, 0), pages - 1);
        List<Row> rows = filtered.subList(current * PAGE_SIZE, Math.min(filtered.size(), (current + 1) * PAGE_SIZE))
                .stream()
                .map(f -> new Row(f, DiscountView.of(f.assessment()), categoryNames.get(f.categoryCode())))
                .toList();

        model.addAttribute("stats", report.shopStats(all));
        model.addAttribute("rows", rows);
        model.addAttribute("total", filtered.size());
        model.addAttribute("page", current);
        model.addAttribute("pages", pages);
        model.addAttribute("levels", LEVELS);
        model.addAttribute("level", level);
        model.addAttribute("shops", catalog.getEnabledShops());
        model.addAttribute("shop", shop);
        model.addAttribute("categories", catalog.getCategories());
        model.addAttribute("category", category);
        model.addAttribute("pageUrlPrefix", pageUrlPrefix(level, shop, category));
        return "discounts/list";
    }

    private static boolean matchesLevel(DiscountFinding finding, String level) {
        DiscountAssessment.Level actual = finding.assessment().level();
        return switch (level.toUpperCase(Locale.ROOT)) {
            case "ALL" -> true;
            case "UNTRUSTWORTHY" -> actual == DiscountAssessment.Level.FAKE
                    || actual == DiscountAssessment.Level.SUSPICIOUS;
            default -> actual.name().equalsIgnoreCase(level);
        };
    }

    private static String pageUrlPrefix(String level, String shop, String category) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/discounts").queryParam("level", level);
        if (shop != null && !shop.isBlank()) {
            uri.queryParam("shop", shop);
        }
        if (category != null && !category.isBlank()) {
            uri.queryParam("category", category);
        }
        uri.queryParam("page", "");
        return uri.encode().build().toUriString();
    }

}
