package org.viewer.pricemeter.web.product;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.viewer.pricemeter.analytics.PriceAnalyticsService;
import org.viewer.pricemeter.analytics.ProductMarket;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.analytics.ProductVerdicts;
import org.viewer.pricemeter.analytics.VerdictService;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.ProductFilter;
import org.viewer.pricemeter.fakediscount.FakeDiscountDetector;
import org.viewer.pricemeter.subscription.service.SubscriptionService;
import org.viewer.pricemeter.web.DiscountView;
import org.viewer.pricemeter.web.PeriodOptions;
import org.viewer.pricemeter.web.VerdictView;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Сторінки товарів — пропозиції різних магазинів, об'єднані зіставленням.
 */
@Controller
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    static final int PAGE_SIZE = 50;

    private final CatalogService catalog;
    private final ProductMarketService markets;
    private final VerdictService verdicts;
    private final FakeDiscountDetector discounts;
    private final SubscriptionService subscriptions;

    /**
     * Товар для шаблону (без лінивих зв'язків сутності) разом із поточним ринком.
     */
    public record ProductView(Long id, String title, String brand, String modelCode, String categoryCode,
                              String categoryName, ProductMarket market) {

        static ProductView of(Product product, ProductMarket market) {
            return new ProductView(product.getId(), product.getTitle(), product.getBrand(), product.getModelCode(),
                    product.getCategory().getCode(), product.getCategory().getName(), market);
        }
    }

    @GetMapping
    public String list(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "false") boolean multi,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        Page<Product> products = catalog.searchProducts(new ProductFilter(category, q, multi),
                PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by("title", "id")));
        Map<Long, ProductMarket> byProduct = markets.markets(products.map(Product::getId).getContent());

        model.addAttribute("products", products.map(product ->
                ProductView.of(product, byProduct.getOrDefault(product.getId(), ProductMarket.EMPTY))));
        model.addAttribute("categories", catalog.getCategories());
        model.addAttribute("category", category);
        model.addAttribute("q", q);
        model.addAttribute("multi", multi);
        model.addAttribute("pageUrlPrefix", pageUrlPrefix(category, q, multi));
        return "products/list";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Authentication user, Model model) {
        Product product = catalog.getProduct(id);
        ProductVerdicts productVerdicts = verdicts.forProduct(id, PriceAnalyticsService.DEFAULT_PERIOD);
        Map<Long, VerdictView> offerVerdicts = new HashMap<>();
        productVerdicts.byOffer().forEach((offerId, verdict) -> offerVerdicts.put(offerId, VerdictView.of(verdict)));

        model.addAttribute("product", ProductView.of(product, productVerdicts.market()));
        model.addAttribute("verdicts", offerVerdicts);
        Map<Long, DiscountView> offerDiscounts = new HashMap<>();
        discounts.assess(productVerdicts.market()).forEach((offerId, assessment) -> {
            DiscountView view = DiscountView.of(assessment);
            if (view != null) {
                offerDiscounts.put(offerId, view);
            }
        });
        model.addAttribute("discounts", offerDiscounts);
        model.addAttribute("bestVerdict", productVerdicts.best().map(VerdictView::of).orElse(null));
        model.addAttribute("bestOffer", productVerdicts.bestOffer().orElse(null));
        model.addAttribute("periods", PeriodOptions.LABELS);
        model.addAttribute("defaultPeriod", PeriodOptions.DEFAULT);
        model.addAttribute("subscription", user == null ? null
                : subscriptions.find(user.getName(), id).orElse(null));
        if (isAdmin(user)) {
            model.addAttribute("matchRows", catalog.getOffersOfProduct(id).stream()
                    .sorted(Comparator.comparing((Offer o) -> o.getShop().getName()).thenComparing(Offer::getId))
                    .map(MatchRow::of)
                    .toList());
        }
        return "products/details";
    }

    /** Пропозиція товару для адміністратора: як її зіставлено і що з цим можна зробити. */
    public record MatchRow(Long offerId, String shopName, String title, MatchStatus status, BigDecimal confidence) {

        static MatchRow of(Offer offer) {
            return new MatchRow(offer.getId(), offer.getShop().getName(), offer.getRawTitle(), offer.getMatchStatus(),
                    offer.getMatchConfidence());
        }

        /** «Той самий товар» вручну ще не підтверджено. */
        public boolean canPin() {
            return status != MatchStatus.MANUAL;
        }

        /** Є ручне рішення, яке можна скасувати. */
        public boolean canReset() {
            return status == MatchStatus.MANUAL || status == MatchStatus.REJECTED;
        }
    }

    private static boolean isAdmin(Authentication user) {
        return user != null && user.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    /** Адреса поточного списку з фільтрами, до якої дописується номер сторінки. */
    private static String pageUrlPrefix(String category, String q, boolean multi) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/products");
        if (category != null && !category.isBlank()) {
            uri.queryParam("category", category);
        }
        if (q != null && !q.isBlank()) {
            uri.queryParam("q", q.strip());
        }
        if (multi) {
            uri.queryParam("multi", true);
        }
        uri.queryParam("page", "");
        return uri.encode().build().toUriString();
    }

}
