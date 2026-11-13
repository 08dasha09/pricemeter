package org.viewer.pricemeter.crawler.scheduling;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /actuator/health} → {@code crawler}: DOWN, якщо успішного обходу не було довше {@link #STALE_AFTER} (історія
 * цін перестала накопичуватись); деталі (адміністратору) — останній обхід і магазини з проблемами.
 */
@Component
@RequiredArgsConstructor
public class CrawlerHealthIndicator implements HealthIndicator {

    static final Duration STALE_AFTER = Duration.ofHours(48);

    private final CrawlJournal journal;
    private final CrawlHealthService crawlHealth;
    private final Clock clock;

    @Override
    public Health health() {
        Optional<Instant> last = journal.lastFinishedStart();
        Map<String, Object> problems = new LinkedHashMap<>();
        crawlHealth.shops().stream()
                .filter(CrawlHealthService.ShopCrawlHealth::problem)
                .forEach(shop -> problems.put(shop.shopCode(), Map.of(
                        "failedCategories", shop.failedCategories(),
                        "errorPercent", shop.errorPercent() != null ? shop.errorPercent() : 0)));
        Health.Builder health = last.isPresent() && Duration.between(last.get(), clock.instant()).compareTo(STALE_AFTER) < 0
                ? Health.up() : Health.down();
        return health
                .withDetail("lastFinishedCrawl", last.map(Instant::toString).orElse("never"))
                .withDetail("shopsWithProblems", problems)
                .build();
    }

}
