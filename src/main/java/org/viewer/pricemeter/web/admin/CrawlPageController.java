package org.viewer.pricemeter.web.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.crawler.scheduling.CrawlAlreadyRunningException;
import org.viewer.pricemeter.crawler.scheduling.CrawlHealthService;
import org.viewer.pricemeter.crawler.scheduling.CrawlJournal;
import org.viewer.pricemeter.crawler.scheduling.CrawlLauncher;
import org.viewer.pricemeter.crawler.scheduling.CrawlRun;
import org.viewer.pricemeter.crawler.scheduling.CrawlScheduler;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Адмін-сторінка: форма запуску обходу, розклад і журнал запусків.
 */
@Controller
@RequestMapping("/admin/crawl")
@RequiredArgsConstructor
public class CrawlPageController {

    static final int DEFAULT_LIMIT = 20;
    /**
     * Ліміт кнопки «Обійти всі зараз», якщо розклад вимкнено. Повний обхід має оновлювати й відомі пропозиції
     * (їх тисячі), тож ліміт той самий, що в розкладі, а не як у форми окремої категорії.
     */
    static final int FULL_CRAWL_LIMIT = 200;
    static final int SHOWN_RUNS = 30;

    private final CrawlLauncher launcher;
    private final CrawlJournal journal;
    private final CrawlHealthService crawlHealth;
    private final CatalogService catalog;
    private final ObjectProvider<CrawlScheduler> scheduler;

    /** Пункт форми: магазин і категорії, які вміє обходити його адаптер. */
    public record ShopTargets(String code, String name, List<Category> categories) {
    }

    /** Магазин, у якого останній обхід пройшов погано (ймовірно, змінилась верстка). */
    public record ShopProblem(String shopName, int failedCategories, Integer errorPercent) {
    }

    @GetMapping
    public String page(Model model) {
        Map<String, String> shopNames = catalog.getEnabledShops().stream()
                .collect(Collectors.toMap(Shop::getCode, Shop::getName));
        Map<String, Category> categories = catalog.getCategories().stream()
                .collect(Collectors.toMap(Category::getCode, Function.identity()));

        List<ShopTargets> targets = launcher.targets().entrySet().stream()
                .map(e -> new ShopTargets(e.getKey(), shopNames.getOrDefault(e.getKey(), e.getKey()),
                        e.getValue().stream().map(categories::get).filter(Objects::nonNull).toList()))
                .toList();
        List<CrawlRunView> runs = journal.recent(SHOWN_RUNS).stream()
                .map(run -> CrawlRunView.of(run,
                        shopNames.getOrDefault(run.getShopCode(), run.getShopCode()),
                        categories.containsKey(run.getCategoryCode())
                                ? categories.get(run.getCategoryCode()).getName() : run.getCategoryCode()))
                .toList();

        model.addAttribute("shopProblems", crawlHealth.shops().stream()
                .filter(CrawlHealthService.ShopCrawlHealth::problem)
                .map(shop -> new ShopProblem(shopNames.getOrDefault(shop.shopCode(), shop.shopCode()),
                        shop.failedCategories(), shop.errorPercent()))
                .toList());
        model.addAttribute("targets", targets);
        model.addAttribute("runs", runs);
        model.addAttribute("anyRunning", runs.stream().anyMatch(run -> run.status() == CrawlRun.Status.RUNNING));
        model.addAttribute("defaultLimit", DEFAULT_LIMIT);
        model.addAttribute("maxLimit", CrawlAdminController.MAX_LIMIT);
        CrawlScheduler schedule = scheduler.getIfAvailable();
        if (schedule != null) {
            model.addAttribute("nextScheduledRun", schedule.nextRun().toLocalDateTime());
            model.addAttribute("scheduledLimit", schedule.limitPerCategory());
        }
        model.addAttribute("fullCrawlLimit", schedule != null ? schedule.limitPerCategory() : FULL_CRAWL_LIMIT);
        return "admin/crawl";
    }

    /**
     * @param target «МАГАЗИН:КАТЕГОРІЯ» з форми
     */
    @PostMapping
    public String launch(@RequestParam String target, @RequestParam(defaultValue = "20") int limit,
                         RedirectAttributes redirect) {
        String[] parts = target.split(":", 2);
        if (parts.length != 2 || !validLimit(limit)) {
            redirect.addFlashAttribute("error",
                    "Оберіть магазин і категорію, ліміт — від 1 до " + CrawlAdminController.MAX_LIMIT);
            return "redirect:/admin/crawl";
        }
        try {
            CrawlRun run = launcher.launch(parts[0], parts[1], limit);
            redirect.addFlashAttribute("message", "Обхід #" + run.getId() + " запущено");
        } catch (NotFoundException e) {
            redirect.addFlashAttribute("error", "Цей магазин не вміє обходити таку категорію");
        } catch (CrawlAlreadyRunningException e) {
            redirect.addFlashAttribute("error", "Обхід цієї категорії магазину вже триває");
        }
        return "redirect:/admin/crawl";
    }

    /** Повний обхід усіх магазинів і категорій — те саме, що робить розклад. */
    @PostMapping("/all")
    public String launchAll(@RequestParam(defaultValue = "20") int limit, RedirectAttributes redirect) {
        if (!validLimit(limit)) {
            redirect.addFlashAttribute("error", "Ліміт — від 1 до " + CrawlAdminController.MAX_LIMIT);
            return "redirect:/admin/crawl";
        }
        launcher.launchAll(CrawlRun.Trigger.MANUAL, limit);
        redirect.addFlashAttribute("message", "Запущено обхід усіх магазинів (до " + limit + " товарів на категорію)");
        redirect.addFlashAttribute("refreshSoon", true);
        return "redirect:/admin/crawl";
    }

    private static boolean validLimit(int limit) {
        return limit >= 1 && limit <= CrawlAdminController.MAX_LIMIT;
    }

}
