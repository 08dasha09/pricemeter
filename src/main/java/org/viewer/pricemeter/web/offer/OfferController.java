package org.viewer.pricemeter.web.offer;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;
import org.viewer.pricemeter.analytics.PriceAnalyticsService;
import org.viewer.pricemeter.analytics.VerdictService;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferFilter;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.fakediscount.FakeDiscountDetector;
import org.viewer.pricemeter.web.DiscountView;
import org.viewer.pricemeter.web.PeriodOptions;
import org.viewer.pricemeter.web.VerdictView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Сторінки пропозицій магазинів (до появи зіставлення товарів).
 */
@Controller
@RequestMapping("/offers")
@RequiredArgsConstructor
public class OfferController {

    static final int PAGE_SIZE = 50;

    private final CatalogService catalog;
    private final PriceHistoryService priceHistory;
    private final VerdictService verdicts;
    private final FakeDiscountDetector discounts;

    @GetMapping
    public String list(@RequestParam(required = false) String shop,
                       @RequestParam(required = false) String category,
                       @RequestParam(required = false) String q,
                       @RequestParam(defaultValue = "false") boolean inactive,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        OfferFilter filter = new OfferFilter(shop, category, q, !inactive);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
                Sort.by(Sort.Order.desc("lastSeenAt"), Sort.Order.desc("id")));
        Page<Offer> offers = catalog.searchOffers(filter, pageable);
        Map<Long, PriceSnapshot> prices = priceHistory.getCurrentPrices(
                offers.getContent().stream().map(Offer::getId).toList());

        model.addAttribute("offers", offers.map(offer -> OfferRow.of(offer, prices.get(offer.getId()))));
        model.addAttribute("shops", catalog.getEnabledShops());
        model.addAttribute("categories", catalog.getCategories());
        model.addAttribute("shop", shop);
        model.addAttribute("category", category);
        model.addAttribute("q", q);
        model.addAttribute("inactive", inactive);
        model.addAttribute("pageUrlPrefix", pageUrlPrefix(shop, category, q, inactive));
        return "offers/list";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        Offer offer = catalog.getOffer(id);
        List<PriceSnapshot> history = priceHistory.getAll(id);
        List<PriceChangeRow> changes = history.reversed().stream()
                .map(PriceChangeRow::of)
                .toList();
        List<BigDecimal> availablePrices = history.stream()
                .filter(s -> s.isAvailable() && s.getPrice() != null)
                .map(PriceSnapshot::getPrice)
                .toList();

        model.addAttribute("offer", OfferRow.of(offer, history.isEmpty() ? null : history.getLast()));
        model.addAttribute("minPrice", availablePrices.stream().min(Comparator.naturalOrder()).orElse(null));
        model.addAttribute("maxPrice", availablePrices.stream().max(Comparator.naturalOrder()).orElse(null));
        model.addAttribute("observedDays", history.isEmpty() ? 0 : ChronoUnit.DAYS.between(
                LocalDate.ofInstant(history.getFirst().getValidFrom(), TimeConfig.ZONE),
                LocalDate.ofInstant(history.getLast().getValidTo(), TimeConfig.ZONE)) + 1);
        model.addAttribute("productId", offer.getProduct() != null ? offer.getProduct().getId() : null);
        model.addAttribute("verdict", VerdictView.of(verdicts.forOffer(id, PriceAnalyticsService.DEFAULT_PERIOD)));
        model.addAttribute("discount", DiscountView.of(discounts.assessOffer(offer)));
        model.addAttribute("brand", offer.getBrandRaw());
        model.addAttribute("mpn", offer.getMpn());
        model.addAttribute("gtin", offer.getGtin());
        model.addAttribute("firstSeenAt", LocalDateTime.ofInstant(offer.getFirstSeenAt(), TimeConfig.ZONE));
        model.addAttribute("changes", changes);
        model.addAttribute("periods", PeriodOptions.LABELS);
        model.addAttribute("defaultPeriod", PeriodOptions.DEFAULT);
        return "offers/details";
    }

    /** Адреса поточного списку з усіма фільтрами, до якої дописується номер сторінки. */
    private static String pageUrlPrefix(String shop, String category, String q, boolean inactive) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromPath("/offers");
        if (shop != null && !shop.isBlank()) {
            uri.queryParam("shop", shop);
        }
        if (category != null && !category.isBlank()) {
            uri.queryParam("category", category);
        }
        if (q != null && !q.isBlank()) {
            uri.queryParam("q", q.strip());
        }
        if (inactive) {
            uri.queryParam("inactive", true);
        }
        uri.queryParam("page", "");
        return uri.encode().build().toUriString();
    }

}
