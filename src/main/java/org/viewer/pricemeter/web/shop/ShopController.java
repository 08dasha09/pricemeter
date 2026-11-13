package org.viewer.pricemeter.web.shop;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.viewer.pricemeter.analytics.ShopPriceIndexService;
import org.viewer.pricemeter.analytics.ShopPriceIndexService.PriceIndexReport;
import org.viewer.pricemeter.analytics.ShopPriceIndexService.ShopPriceIndex;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.crawler.scheduling.CrawlHealthService;
import org.viewer.pricemeter.fakediscount.DiscountReportService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сторінка магазину: наскільки він дорожчий чи дешевший за інші (загалом і по категоріях), його знижки та стан збору.
 */
@Controller
@RequiredArgsConstructor
public class ShopController {

    private final CatalogService catalog;
    private final ShopPriceIndexService priceIndex;
    private final DiscountReportService discounts;
    private final CrawlHealthService crawlHealth;

    /** Індекс магазину в категорії. */
    public record CategoryIndex(String categoryCode, String categoryName, ShopPriceIndex index) {
    }

    @GetMapping("/shops/{code}")
    public String shop(@PathVariable String code, Model model) {
        Shop shop = catalog.getShop(code.toUpperCase(Locale.ROOT));
        Map<String, String> categoryNames = catalog.getCategories().stream()
                .collect(Collectors.toMap(Category::getCode, Category::getName));
        PriceIndexReport report = priceIndex.report();
        List<CategoryIndex> byCategory = report.byCategory().entrySet().stream()
                .map(e -> e.getValue().stream().filter(i -> i.shopCode().equals(shop.getCode())).findFirst()
                        .map(i -> new CategoryIndex(e.getKey(), categoryNames.getOrDefault(e.getKey(), e.getKey()), i))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();

        model.addAttribute("shop", shop);
        model.addAttribute("activeOffers", catalog.countActiveOffers(shop.getCode()));
        model.addAttribute("index", report.overallOf(shop.getCode()));
        model.addAttribute("shopsCompared", report.overall().size());
        model.addAttribute("byCategory", byCategory);
        model.addAttribute("discounts", discounts.shopStats(discounts.findings()).stream()
                .filter(s -> s.shopCode().equals(shop.getCode())).findFirst().orElse(null));
        CrawlHealthService.ShopCrawlHealth crawl = crawlHealth.shops().stream()
                .filter(h -> h.shopCode().equals(shop.getCode())).findFirst().orElse(null);
        model.addAttribute("crawl", crawl);
        model.addAttribute("lastCrawl", crawl != null && crawl.lastRunAt() != null
                ? LocalDateTime.ofInstant(crawl.lastRunAt(), TimeConfig.ZONE) : null);
        return "shops/details";
    }

}
