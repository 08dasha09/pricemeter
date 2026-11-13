package org.viewer.pricemeter.crawler.scheduling;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.viewer.pricemeter.common.config.TimeConfig;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * Щоденний обхід усіх магазинів. ShedLock гарантує, що обхід не запуститься вдруге, поки триває
 * попередній (і не запуститься паралельно на кількох екземплярах застосунку).
 * Бін створює {@link SchedulingConfig}, якщо {@code crawler.schedule.enabled=true}.
 */
public class CrawlScheduler {

    private final CrawlLauncher launcher;
    private final Clock clock;
    private final CronExpression cron;
    private final int limitPerCategory;

    public CrawlScheduler(CrawlLauncher launcher, Clock clock, String cron, int limitPerCategory) {
        this.launcher = launcher;
        this.clock = clock;
        this.cron = CronExpression.parse(cron);
        this.limitPerCategory = limitPerCategory;
    }

    @Scheduled(cron = "${crawler.schedule.cron}", zone = "Europe/Kyiv")
    @SchedulerLock(name = "dailyCrawl", lockAtMostFor = "${crawler.schedule.lock-at-most-for}", lockAtLeastFor = "PT5M")
    public void dailyCrawl() {
        launcher.crawlAll(CrawlRun.Trigger.SCHEDULED, limitPerCategory);
    }

    /**
     * Надолужити пропущений щоденний обхід (див. {@link MissedCrawlCatchUp}). Той самий замок, що й у щоденного: якщо
     * він уже йде, виклик нічого не робить.
     */
    @SchedulerLock(name = "dailyCrawl", lockAtMostFor = "${crawler.schedule.lock-at-most-for}", lockAtLeastFor = "PT5M")
    public void catchUpCrawl() {
        launcher.crawlAll(CrawlRun.Trigger.SCHEDULED, limitPerCategory);
    }

    /** Коли наступний обхід за розкладом (київський час). */
    public ZonedDateTime nextRun() {
        return cron.next(ZonedDateTime.now(clock.withZone(TimeConfig.ZONE)));
    }

    public int limitPerCategory() {
        return limitPerCategory;
    }

}
