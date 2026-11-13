package org.viewer.pricemeter.crawler.scheduling;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.viewer.pricemeter.common.config.TimeConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MissedCrawlCatchUpTest {

    /** 2026-10-19 10:00 за Києвом; наступний обхід за розкладом — 20.10 о 04:00. */
    private static final Instant NOW = Instant.parse("2026-10-19T07:00:00Z");

    private final CrawlScheduler scheduler = mock(CrawlScheduler.class);
    private final CrawlJournal journal = mock(CrawlJournal.class);
    private final TaskScheduler tasks = mock(TaskScheduler.class);
    private final MissedCrawlCatchUp catchUp = new MissedCrawlCatchUp(scheduler, journal, tasks,
            Clock.fixed(NOW, TimeConfig.ZONE), Duration.ofHours(20), Duration.ofMinutes(2));

    @Test
    void catchesUpAfterDelayWhenLastCrawlIsOld() {
        nextScheduled(ZonedDateTime.of(2026, 10, 20, 4, 0, 0, 0, TimeConfig.ZONE));
        when(journal.lastFinishedStart()).thenReturn(Optional.of(NOW.minus(Duration.ofHours(30))));

        catchUp.onStartup();

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(tasks).schedule(task.capture(), eq(NOW.plus(Duration.ofMinutes(2))));
        task.getValue().run();
        verify(scheduler).catchUpCrawl();
    }

    @Test
    void catchesUpWhenNeverCrawled() {
        nextScheduled(ZonedDateTime.of(2026, 10, 20, 4, 0, 0, 0, TimeConfig.ZONE));
        when(journal.lastFinishedStart()).thenReturn(Optional.empty());

        catchUp.onStartup();

        verify(tasks).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void nothingToCatchUpAfterRecentCrawl() {
        nextScheduled(ZonedDateTime.of(2026, 10, 20, 4, 0, 0, 0, TimeConfig.ZONE));
        when(journal.lastFinishedStart()).thenReturn(Optional.of(NOW.minus(Duration.ofHours(5))));

        catchUp.onStartup();

        verify(tasks, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void scheduledCrawlSoonWillCatchUpItself() {
        // старт о 02:30 — о 04:00 обхід за розкладом
        nextScheduled(ZonedDateTime.ofInstant(NOW.plus(Duration.ofMinutes(90)), TimeConfig.ZONE));
        when(journal.lastFinishedStart()).thenReturn(Optional.of(NOW.minus(Duration.ofHours(30))));

        catchUp.onStartup();

        verify(tasks, never()).schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void skipsWhenCrawlWasRunManuallyDuringDelay() {
        nextScheduled(ZonedDateTime.of(2026, 10, 20, 4, 0, 0, 0, TimeConfig.ZONE));
        when(journal.lastFinishedStart()).thenReturn(Optional.of(NOW));

        catchUp.catchUpIfStillMissed();

        verify(scheduler, never()).catchUpCrawl();
    }

    private void nextScheduled(ZonedDateTime next) {
        when(scheduler.nextRun()).thenReturn(next);
    }

}
