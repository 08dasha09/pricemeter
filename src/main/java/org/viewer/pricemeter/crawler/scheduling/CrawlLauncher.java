package org.viewer.pricemeter.crawler.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.crawler.CrawlCompletedEvent;
import org.viewer.pricemeter.crawler.CrawlReport;
import org.viewer.pricemeter.crawler.CrawlService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Запуск обходів із записом у журнал: окрема категорія (вручну) або всі магазини (за розкладом).
 * Магазини обходяться паралельно, категорії в межах магазину — послідовно, щоб не перевищувати
 * темп запитів до одного сайту. Після обходу публікується {@link CrawlCompletedEvent}.
 */
@Slf4j
@Service
public class CrawlLauncher {

    private final CrawlService crawlService;
    private final CrawlJournal journal;
    private final CatalogService catalog;
    private final Clock clock;
    private final Duration deactivateAfter;
    private final Executor background;
    private final ApplicationEventPublisher events;

    @Autowired
    public CrawlLauncher(CrawlService crawlService, CrawlJournal journal, CatalogService catalog, Clock clock,
                         @Value("${crawler.deactivate-after-days:7}") int deactivateAfterDays,
                         ApplicationEventPublisher events) {
        this(crawlService, journal, catalog, clock, Duration.ofDays(deactivateAfterDays),
                Executors.newVirtualThreadPerTaskExecutor(), events);
    }

    CrawlLauncher(CrawlService crawlService, CrawlJournal journal, CatalogService catalog, Clock clock,
                  Duration deactivateAfter, Executor background, ApplicationEventPublisher events) {
        this.crawlService = crawlService;
        this.journal = journal;
        this.catalog = catalog;
        this.clock = clock;
        this.deactivateAfter = deactivateAfter;
        this.background = background;
        this.events = events;
    }

    /**
     * Запускає обхід категорії у фоні.
     *
     * @throws NotFoundException            магазин вимкнено або категорія не підтримується
     * @throws CrawlAlreadyRunningException обхід цієї категорії магазину вже триває
     */
    public CrawlRun launch(String shopCode, String categoryCode, int limit) {
        CrawlRun run = startManual(shopCode, categoryCode, limit);
        background.execute(() -> {
            execute(run);
            completed(Set.of(run.getCategoryCode()));
        });
        return run;
    }

    /**
     * Обходить категорію в поточному потоці (для синхронного запуску з {@code curl}).
     *
     * @throws NotFoundException            магазин вимкнено або категорія не підтримується
     * @throws CrawlAlreadyRunningException обхід цієї категорії магазину вже триває
     */
    public CrawlReport crawlNow(String shopCode, String categoryCode, int limit) {
        CrawlRun run = startManual(shopCode, categoryCode, limit);
        try {
            CrawlReport report = crawlService.crawlCategory(run.getShopCode(), run.getCategoryCode(), limit);
            journal.finish(run.getId(), report);
            return report;
        } catch (RuntimeException e) {
            journal.fail(run.getId(), describe(e));
            throw e;
        } finally {
            completed(Set.of(run.getCategoryCode()));
        }
    }

    private CrawlRun startManual(String shopCode, String categoryCode, int limit) {
        String shop = shopCode.strip().toUpperCase(Locale.ROOT);
        String category = categoryCode.strip().toUpperCase(Locale.ROOT);
        if (!targets().getOrDefault(shop, List.of()).contains(category)) {
            throw NotFoundException.of("Category " + category + " for shop", shop);
        }
        return journal.start(shop, category, CrawlRun.Trigger.MANUAL, limit);
    }

    /** {@link #crawlAll} у фоні. */
    public void launchAll(CrawlRun.Trigger trigger, int limitPerCategory) {
        background.execute(() -> crawlAll(trigger, limitPerCategory));
    }

    /**
     * Обходить усі категорії всіх увімкнених магазинів і чекає завершення. Категорії, обхід яких
     * уже триває, пропускаються. Якщо жодна категорія магазину не впала, його пропозиції, яких не бачили
     * довше за {@code crawler.deactivate-after-days}, позначаються неактивними (якщо обхід упав, напр. через
     * зміну верстки, «не бачили» не означає «зникли»).
     *
     * @return кількість виконаних запусків
     */
    public int crawlAll(CrawlRun.Trigger trigger, int limitPerCategory) {
        Map<String, List<String>> targets = targets();
        log.info("Crawling all shops ({}): {}", trigger, targets);
        AtomicInteger runs = new AtomicInteger();
        Set<String> crawledCategories = ConcurrentHashMap.newKeySet();
        try (ExecutorService shops = Executors.newVirtualThreadPerTaskExecutor()) {
            targets.forEach((shop, categories) -> shops.execute(() -> {
                boolean allSucceeded = true;
                for (String category : categories) {
                    CrawlRun run;
                    try {
                        run = journal.start(shop, category, trigger, limitPerCategory);
                    } catch (CrawlAlreadyRunningException e) {
                        log.info("Skipping {} {}: already running", shop, category);
                        continue;
                    }
                    allSucceeded &= execute(run);
                    crawledCategories.add(category);
                    runs.incrementAndGet();
                }
                if (allSucceeded) {
                    deactivateStale(shop);
                } else {
                    log.warn("Not deactivating stale offers of {}: some categories failed", shop);
                }
            }));
        }
        log.info("Crawl of all shops ({}) finished: {} runs", trigger, runs.get());
        if (!crawledCategories.isEmpty()) {
            completed(new TreeSet<>(crawledCategories));
        }
        return runs.get();
    }

    /** Увімкнені магазини, для яких є адаптер → категорії, які він уміє обходити. */
    public Map<String, List<String>> targets() {
        Set<String> enabled = catalog.getEnabledShops().stream().map(Shop::getCode).collect(Collectors.toSet());
        return crawlService.supportedCategories().entrySet().stream()
                .filter(e -> enabled.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, TreeMap::new));
    }

    /** @return {@code true}, якщо обхід завершився без збою */
    private boolean execute(CrawlRun run) {
        try {
            journal.finish(run.getId(),
                    crawlService.crawlCategory(run.getShopCode(), run.getCategoryCode(), run.getLimit()));
            return true;
        } catch (RuntimeException e) {
            log.error("Crawl {} {} failed", run.getShopCode(), run.getCategoryCode(), e);
            journal.fail(run.getId(), describe(e));
            return false;
        }
    }

    /** Помилка слухача (напр. зіставлення) не повинна зламати обхід. */
    private void completed(Set<String> categoryCodes) {
        try {
            events.publishEvent(new CrawlCompletedEvent(categoryCodes));
        } catch (RuntimeException e) {
            log.error("Handling of completed crawl {} failed", categoryCodes, e);
        }
    }

    private void deactivateStale(String shop) {
        Instant threshold = clock.instant().minus(deactivateAfter);
        int deactivated = catalog.deactivateOffersNotSeenSince(shop, threshold);
        if (deactivated > 0) {
            log.info("Deactivated {} offers of {} not seen since {}", deactivated, shop, threshold);
        }
    }

    private static String describe(RuntimeException e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

}
