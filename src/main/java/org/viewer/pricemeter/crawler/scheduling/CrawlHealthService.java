package org.viewer.pricemeter.crawler.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Стан збору по магазинах за останнім завершеним запуском кожної категорії: чи не впали категорії і яка частка
 * сторінок товарів не розібралась. Різке зростання помилок зазвичай означає зміну верстки магазину.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrawlHealthService {

    /** Частка помилок, з якої магазин вважається проблемним. */
    public static final int ERROR_PERCENT_THRESHOLD = 20;

    private final CrawlRunRepository runs;

    public List<ShopCrawlHealth> shops() {
        Map<String, List<CrawlRun>> byShop = runs.findLatestCompletedPerCategory().stream()
                .collect(Collectors.groupingBy(CrawlRun::getShopCode, TreeMap::new, Collectors.toList()));
        return byShop.entrySet().stream().map(e -> ShopCrawlHealth.of(e.getKey(), e.getValue())).toList();
    }

    /**
     * @param lastRunAt        коли почався найпізніший із цих запусків
     * @param failedCategories категорії, обхід яких упав повністю
     * @param failedPages      сторінки товарів, які не вдалося завантажити чи розібрати
     * @param errorPercent     частка таких сторінок серед оброблених, %; {@code null} — сторінок не було
     */
    public record ShopCrawlHealth(String shopCode, Instant lastRunAt, int categories, int failedCategories,
                                  int failedPages, Integer errorPercent) {

        static ShopCrawlHealth of(String shopCode, List<CrawlRun> latest) {
            int failedCategories = (int) latest.stream().filter(r -> r.getStatus() == CrawlRun.Status.FAILED).count();
            int failed = latest.stream().map(CrawlRun::getFailed).filter(Objects::nonNull).mapToInt(i -> i).sum();
            int processed = failed + latest.stream()
                    .mapToInt(r -> zeroIfNull(r.getSaved()) + zeroIfNull(r.getSkipped()))
                    .sum();
            Instant lastRunAt = latest.stream().map(CrawlRun::getStartedAt).max(Comparator.naturalOrder()).orElse(null);
            return new ShopCrawlHealth(shopCode, lastRunAt, latest.size(), failedCategories, failed,
                    processed > 0 ? Math.round(failed * 100f / processed) : null);
        }

        /** Категорія впала або помилок ≥ {@link #ERROR_PERCENT_THRESHOLD}%. */
        public boolean problem() {
            return failedCategories > 0 || (errorPercent != null && errorPercent >= ERROR_PERCENT_THRESHOLD);
        }

        private static int zeroIfNull(Integer value) {
            return value != null ? value : 0;
        }
    }

}
