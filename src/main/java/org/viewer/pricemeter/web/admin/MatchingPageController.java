package org.viewer.pricemeter.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.matching.MatchingAlreadyRunningException;
import org.viewer.pricemeter.matching.MatchingReport;
import org.viewer.pricemeter.matching.MatchingService;
import org.viewer.pricemeter.matching.PendingReview;
import org.viewer.pricemeter.matching.similarity.MatchScore;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Адмін-сторінка зіставлення: пропозиції, що чекають перевірки, з товарами-кандидатами; ручні рішення.
 * JSON-запуск для {@code curl} — {@link MatchingAdminController}.
 */
@Controller
@RequestMapping("/admin/matching")
@RequiredArgsConstructor
public class MatchingPageController {

    static final int MAX_SUGGESTIONS = 3;

    /** Пояснення вирішальної ознаки для модератора. */
    static final Map<MatchScore.Reason, String> REASONS = Map.of(
            MatchScore.Reason.BLOCKED, "різні категорії чи бренди",
            MatchScore.Reason.GTIN, "однаковий штрихкод",
            MatchScore.Reason.GTIN_CONFLICT, "різні штрихкоди",
            MatchScore.Reason.EXACT_CODE, "однаковий код моделі",
            MatchScore.Reason.BASE_CODE, "код відрізняється лише регіоном",
            MatchScore.Reason.CODE_PREFIX, "один код — початок іншого",
            MatchScore.Reason.SIMILAR_CODE, "ті самі цифри коду, інші літери",
            MatchScore.Reason.DIFFERENT_CODE, "різні коди моделі",
            MatchScore.Reason.TITLE_ONLY, "лише за назвою — коду немає");

    private final MatchingService matching;
    private final CatalogService catalog;

    @GetMapping
    public String page(Model model) {
        List<PendingReview> reviews = matching.pendingReviews(MAX_SUGGESTIONS);
        model.addAttribute("reviews", reviews);
        model.addAttribute("shopNames", catalog.getEnabledShops().stream()
                .collect(Collectors.toMap(Shop::getCode, Shop::getName)));
        model.addAttribute("categoryNames", catalog.getCategories().stream()
                .collect(Collectors.toMap(Category::getCode, Category::getName)));
        model.addAttribute("reasons", REASONS);
        return "admin/matching";
    }

    @PostMapping("/run")
    public String runAll(RedirectAttributes redirect) {
        return act(redirect, 0, null, () -> {
            List<MatchingReport> reports = matching.matchAll();
            return "Зіставлено: " + reports.stream().mapToInt(MatchingReport::offers).sum() + " пропозицій → "
                    + reports.stream().mapToInt(MatchingReport::products).sum() + " товарів, з кількох магазинів — "
                    + reports.stream().mapToInt(MatchingReport::multiShop).sum() + ", на перевірку — "
                    + reports.stream().mapToInt(MatchingReport::pending).sum();
        });
    }

    /** @param product товар, зі сторінки якого прийшли (повернутися туди); без нього — до списку перевірки */
    @PostMapping("/offers/{offerId}/assign")
    public String assign(@PathVariable long offerId, @RequestParam long productId,
                         @RequestParam(required = false) Long product, RedirectAttributes redirect) {
        return act(redirect, offerId, product, () -> {
            matching.assignToProduct(offerId, productId);
            return "Пропозицію #" + offerId + " закріплено за товаром #" + productId;
        });
    }

    @PostMapping("/offers/{offerId}/separate")
    public String separate(@PathVariable long offerId, @RequestParam(required = false) Long product,
                           RedirectAttributes redirect) {
        return act(redirect, offerId, product, () -> {
            matching.keepSeparate(offerId);
            return "Пропозиція #" + offerId + " — окремий товар";
        });
    }

    /** Скасувати ручне рішення — пропозиція знову зіставляється автоматично. */
    @PostMapping("/offers/{offerId}/reset")
    public String reset(@PathVariable long offerId, @RequestParam(required = false) Long product,
                        RedirectAttributes redirect) {
        return act(redirect, offerId, product, () -> {
            matching.resetDecision(offerId);
            return "Ручне рішення щодо пропозиції #" + offerId + " скасовано — зіставлено автоматично";
        });
    }

    /**
     * Виконує рішення й повертає туди, звідки прийшли: на сторінку товару (якщо він після перерахунку ще існує,
     * інакше — на сторінку пропозиції) або до списку перевірки.
     */
    private String act(RedirectAttributes redirect, long offerId, Long product, Supplier<String> action) {
        try {
            redirect.addFlashAttribute("message", action.get());
        } catch (MatchingAlreadyRunningException e) {
            redirect.addFlashAttribute("error", "Зіставлення вже виконується — спробуйте за хвилину");
        } catch (NotFoundException | IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        if (product == null) {
            return "redirect:/admin/matching";
        }
        return catalog.productExists(product) ? "redirect:/products/" + product : "redirect:/offers/" + offerId;
    }

}
