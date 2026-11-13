package org.viewer.pricemeter.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.crawler.scheduling.CrawlJournal;
import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.fakediscount.DiscountFinding;
import org.viewer.pricemeter.fakediscount.DiscountReportService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Головна: пошук товару, розмір каталогу й добірки знижок — справжні (найбільша економія проти інших магазинів) і
 * недостовірні (спершу фіктивні, далі підозрілі; від найглибшої заявленої знижки).
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    static final int PICKS = 6;

    private final CatalogService catalog;
    private final DiscountReportService discounts;
    private final CrawlJournal journal;

    /** Знижка в добірці. */
    public record Pick(DiscountFinding finding, DiscountView view, String categoryName) {
    }

    @GetMapping("/")
    public String home(Model model) {
        List<DiscountFinding> findings = discounts.findings();
        Map<String, String> categoryNames = catalog.getCategories().stream()
                .collect(Collectors.toMap(Category::getCode, Category::getName));

        model.addAttribute("categories", catalog.getCategories());
        model.addAttribute("shops", catalog.getEnabledShops());
        model.addAttribute("overview", catalog.overview());
        model.addAttribute("lastCrawl", journal.lastFinishedStart()
                .map(at -> LocalDateTime.ofInstant(at, TimeConfig.ZONE)).orElse(null));
        model.addAttribute("realDeals", picks(realDeals(findings), categoryNames));
        model.addAttribute("suspicious", picks(untrustworthy(findings), categoryNames));
        model.addAttribute("checkedDiscounts", findings.stream()
                .filter(f -> f.assessment().level() != DiscountAssessment.Level.UNKNOWN).count());
        model.addAttribute("untrustworthyDiscounts", findings.stream().filter(HomeController::isUntrustworthy).count());
        return "home";
    }

    /** Справжні знижки, від найбільшої економії проти медіани інших магазинів. */
    static List<DiscountFinding> realDeals(List<DiscountFinding> findings) {
        return findings.stream()
                .filter(f -> f.assessment().level() == DiscountAssessment.Level.GENUINE)
                .sorted(Comparator.comparingInt((DiscountFinding f) -> f.assessment().marketDeviationPercent())
                        .thenComparingLong(f -> f.offer().id()))
                .limit(PICKS)
                .toList();
    }

    /** Фіктивні й підозрілі — у порядку звіту (спершу фіктивні, від найвищої оцінки й найглибшої знижки). */
    static List<DiscountFinding> untrustworthy(List<DiscountFinding> findings) {
        return findings.stream().filter(HomeController::isUntrustworthy).limit(PICKS).toList();
    }

    private static boolean isUntrustworthy(DiscountFinding f) {
        return f.assessment().level() == DiscountAssessment.Level.FAKE
                || f.assessment().level() == DiscountAssessment.Level.SUSPICIOUS;
    }

    private static List<Pick> picks(List<DiscountFinding> findings, Map<String, String> categoryNames) {
        return findings.stream()
                .map(f -> new Pick(f, DiscountView.of(f.assessment()), categoryNames.get(f.categoryCode())))
                .toList();
    }

}
