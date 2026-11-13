package org.viewer.pricemeter.crawler.scheduling;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.common.config.TimeConfig;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CrawlSchedulerTest {

    private final CrawlLauncher launcher = mock(CrawlLauncher.class);

    @Test
    void dailyCrawlRunsAllShopsWithConfiguredLimit() {
        CrawlScheduler scheduler = scheduler(Instant.parse("2026-10-06T12:00:00Z"));

        scheduler.dailyCrawl();

        verify(launcher).crawlAll(CrawlRun.Trigger.SCHEDULED, 200);
    }

    @Test
    void nextRunIsComputedInKyivTime() {
        // 2026-10-06 15:00 за Києвом → сьогоднішні 04:00 вже минули
        assertThat(scheduler(Instant.parse("2026-10-06T12:00:00Z")).nextRun())
                .isEqualTo(ZonedDateTime.of(2026, 10, 7, 4, 0, 0, 0, TimeConfig.ZONE));
        // 2026-10-07 02:30 за Києвом (23:30 UTC попередньої доби) → сьогодні о 04:00
        assertThat(scheduler(Instant.parse("2026-10-06T23:30:00Z")).nextRun())
                .isEqualTo(ZonedDateTime.of(2026, 10, 7, 4, 0, 0, 0, TimeConfig.ZONE));
    }

    private CrawlScheduler scheduler(Instant now) {
        return new CrawlScheduler(launcher, Clock.fixed(now, TimeConfig.ZONE), "0 0 4 * * *", 200);
    }

}
