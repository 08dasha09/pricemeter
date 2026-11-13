package org.viewer.pricemeter.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.matching.evaluation.LabelingService;
import org.viewer.pricemeter.matching.evaluation.LabelingTask;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Розмітка пар пропозицій «той самий товар / різні» — вибірка для оцінки якості зіставлення.
 */
@Controller
@RequestMapping("/admin/labeling")
@RequiredArgsConstructor
public class LabelingPageController {

    private final LabelingService labeling;
    private final CatalogService catalog;
    private final PriceHistoryService priceHistory;

    /** Пропозиція пари для шаблону; ціна — {@code null}, якщо зараз немає в наявності. */
    public record SideView(long offerId, String shopCode, String shopName, String title, String url,
                           String modelCode, BigDecimal price) {
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("progress", labeling.progress());
        model.addAttribute("reasons", MatchingPageController.REASONS);
        labeling.nextTask().ifPresent(task -> {
            List<Long> ids = List.of(task.a().offerId(), task.b().offerId());
            Map<Long, Offer> offers = catalog.getOffers(ids).stream()
                    .collect(Collectors.toMap(Offer::getId, Function.identity()));
            Map<Long, PriceSnapshot> prices = priceHistory.getCurrentPrices(ids);
            Map<String, String> shopNames = catalog.getEnabledShops().stream()
                    .collect(Collectors.toMap(Shop::getCode, Shop::getName));
            model.addAttribute("task", task);
            SideView a = side(task.a(), offers, prices, shopNames);
            SideView b = side(task.b(), offers, prices, shopNames);
            model.addAttribute("a", a);
            model.addAttribute("b", b);
            model.addAttribute("sides", List.of(a, b));
            model.addAttribute("categoryName", catalog.getCategories().stream()
                    .filter(c -> c.getCode().equals(task.categoryCode()))
                    .map(Category::getName)
                    .findFirst()
                    .orElse(task.categoryCode()));
        });
        return "admin/labeling";
    }

    @PostMapping
    public String label(@RequestParam long offerA, @RequestParam long offerB, @RequestParam boolean same,
                        RedirectAttributes redirect) {
        try {
            labeling.label(offerA, offerB, same);
            redirect.addFlashAttribute("message", "#" + offerA + " і #" + offerB + " — "
                    + (same ? "той самий товар" : "різні товари"));
        } catch (NotFoundException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/labeling";
    }

    private static SideView side(LabelingTask.Side side, Map<Long, Offer> offers, Map<Long, PriceSnapshot> prices,
                                 Map<String, String> shopNames) {
        Offer offer = offers.get(side.offerId());
        PriceSnapshot price = prices.get(side.offerId());
        return new SideView(side.offerId(), side.shopCode(), shopNames.getOrDefault(side.shopCode(), side.shopCode()),
                side.title(), offer != null ? offer.getUrl() : null, side.modelCode(),
                price != null && price.isAvailable() ? price.getPrice() : null);
    }

}
