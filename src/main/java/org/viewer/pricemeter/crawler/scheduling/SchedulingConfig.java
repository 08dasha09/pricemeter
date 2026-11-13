package org.viewer.pricemeter.crawler.scheduling;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;

/**
 * Розклад обходів; вимикається {@code crawler.schedule.enabled=false} (напр. у тестах).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(name = "crawler.schedule.enabled", matchIfMissing = true)
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT23H")
public class SchedulingConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()
                .build());
    }

    @Bean
    CrawlScheduler crawlScheduler(CrawlLauncher launcher, Clock clock,
                                  @Value("${crawler.schedule.cron}") String cron,
                                  @Value("${crawler.schedule.limit-per-category}") int limitPerCategory) {
        return new CrawlScheduler(launcher, clock, cron, limitPerCategory);
    }

    @Bean
    @ConditionalOnBooleanProperty(name = "crawler.schedule.catch-up", matchIfMissing = true)
    MissedCrawlCatchUp missedCrawlCatchUp(CrawlScheduler scheduler, CrawlJournal journal, TaskScheduler tasks,
                                          Clock clock,
                                          @Value("${crawler.schedule.catch-up-after:PT20H}") Duration after,
                                          @Value("${crawler.schedule.catch-up-delay:PT2M}") Duration delay) {
        return new MissedCrawlCatchUp(scheduler, journal, tasks, clock, after, delay);
    }

}
