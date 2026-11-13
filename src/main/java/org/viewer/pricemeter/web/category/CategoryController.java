package org.viewer.pricemeter.web.category;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.viewer.pricemeter.analytics.ShopPriceIndexService;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.ProductFilter;
import org.viewer.pricemeter.fakediscount.DiscountFinding;
import org.viewer.pricemeter.fakediscount.DiscountReportService;

import java.util.List;
import java.util.Locale;

/**
 * Сторінка категорії: скільки товарів, які магазини в ній дешевші чи дорожчі за інші, частка недостовірних знижок.
 */
@Controller
@RequiredArgsConstructor
public class CategoryController {

    private final CatalogService catalog;
    private final ShopPriceIndexService priceIndex;
    private final DiscountReportService discounts;

    @GetMapping("/categories/{code}")
    public String category(@PathVariable String code, Model model) {
        Category category = catalog.getCategory(code.toUpperCase(Locale.ROOT));
        List<DiscountFinding> findings = discounts.findings().stream()
                .filter(f -> category.getCode().equals(f.categoryCode()))
                .toList();

        model.addAttribute("category", category);
        model.addAttribute("products", count(new ProductFilter(category.getCode(), null, false)));
        model.addAttribute("multiShopProducts", count(new ProductFilter(category.getCode(), null, true)));
        model.addAttribute("shops", priceIndex.report().byCategory().getOrDefault(category.getCode(), List.of()));
        model.addAttribute("discountStats", discounts.shopStats(findings));
        return "categories/details";
    }

    private long count(ProductFilter filter) {
        return catalog.searchProducts(filter, PageRequest.of(0, 1)).getTotalElements();
    }

}
