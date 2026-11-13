package org.viewer.pricemeter.crawler.scheduling;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Надолужує пропущений щоденний обхід: застосунок працює не постійно (ноутбук), тож о 04:00 його часто немає. Після
 * старту, якщо останній успішний обхід був давніше за {@code crawler.schedule.catch-up-after} і найближчий обхід за
 * розкладом не скоро, через {@code crawler.schedule.catch-up-delay} запускається повний обхід. Перед запуском умова
 * перевіряється ще раз (за цей час обхід могли запустити вручну).
 * Бін створює {@link SchedulingConfig}, якщо {@code crawler.schedule.catch-up=true}.
 */
@Slf4j
public class MissedCrawlCatchUp {

    /** Якщо обхід за розкладом ближче, ніж це, — він і надолужить. */
    static final Duration SCHEDULED_SOON = Duration.ofHours(2);

    private final CrawlScheduler scheduler;
    private final CrawlJournal journal;
    private final TaskScheduler tasks;
    private final Clock clock;
    private final Duration after;
    private final Duration delay;

    public MissedCrawlCatchUp(CrawlScheduler scheduler, CrawlJournal journal, TaskScheduler tasks, Clock clock,
                              Duration after, Duration delay) {
        this.scheduler = scheduler;
        this.journal = journal;
        this.tasks = tasks;
        this.clock = clock;
        this.after = after;
        this.delay = delay;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (!isMissed()) {
            return;
        }
        Instant at = clock.instant().plus(delay);
        log.info("Daily crawl was missed (last finished run: {}); catching up at {}",
                journal.lastFinishedStart().map(Instant::toString).orElse("never"), at);
        tasks.schedule(this::catchUpIfStillMissed, at);
    }

    void catchUpIfStillMissed() {
        if (isMissed()) {
            scheduler.catchUpCrawl();
        } else {
            log.info("Catch-up crawl is not needed any more");
        }
    }

    /** Останній успішний обхід давно (або його не було), і щоденний обхід — не найближчим часом. */
    boolean isMissed() {
        Instant now = clock.instant();
        Optional<Instant> last = journal.lastFinishedStart();
        if (last.isPresent() && Duration.between(last.get(), now).compareTo(after) < 0) {
            return false;
        }
        return Duration.between(now, scheduler.nextRun().toInstant()).compareTo(SCHEDULED_SOON) > 0;
    }

}
