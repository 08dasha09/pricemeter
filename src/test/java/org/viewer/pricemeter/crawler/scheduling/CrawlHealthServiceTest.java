package org.viewer.pricemeter.crawler.scheduling;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.crawler.CrawlReport;
import org.viewer.pricemeter.crawler.scheduling.CrawlHealthService.ShopCrawlHealth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class, CrawlJournal.class, CrawlHealthService.class})
class CrawlHealthServiceTest {

    @Autowired
    private CrawlJournal journal;

    @Autowired
    private CrawlHealthService health;

    @Test
    void judgesEachShopByLatestRunOfEveryCategory() {
        // Епіцентр: учора все гаразд, сьогодні пральні — 30 помилок зі 100 сторінок
        finished("EPICENTR", "WASHING_MACHINES", 100, 0);
        finished("EPICENTR", "WASHING_MACHINES", 70, 30);
        finished("EPICENTR", "REFRIGERATORS", 100, 0);
        // Moyo: одна категорія впала повністю, сторінок з помилками немає
        finished("MOYO", "WASHING_MACHINES", 50, 1);
        journal.fail(journal.start("MOYO", "REFRIGERATORS", CrawlRun.Trigger.MANUAL, 10).getId(), "layout changed");
        // Цитрус: учора впала, сьогодні гаразд
        journal.fail(journal.start("CITRUS", "DISHWASHERS", CrawlRun.Trigger.MANUAL, 10).getId(), "timeout");
        finished("CITRUS", "DISHWASHERS", 40, 2);
        // ще триває — не враховується
        journal.start("ELDORADO", "MICROWAVES", CrawlRun.Trigger.MANUAL, 10);

        List<ShopCrawlHealth> shops = health.shops();

        assertThat(shops).extracting(ShopCrawlHealth::shopCode).containsExactly("CITRUS", "EPICENTR", "MOYO");
        ShopCrawlHealth citrus = shops.get(0);
        assertThat(citrus.problem()).isFalse();
        assertThat(citrus.errorPercent()).isEqualTo(5);
        ShopCrawlHealth epicentr = shops.get(1);
        assertThat(epicentr.categories()).isEqualTo(2);
        assertThat(epicentr.errorPercent()).isEqualTo(15);
        assertThat(epicentr.problem()).isFalse();
        ShopCrawlHealth moyo = shops.get(2);
        assertThat(moyo.failedCategories()).isEqualTo(1);
        assertThat(moyo.problem()).isTrue();
    }

    @Test
    void highErrorShareIsProblem() {
        finished("EPICENTR", "WASHING_MACHINES", 75, 25);

        assertThat(health.shops()).singleElement().satisfies(shop -> {
            assertThat(shop.errorPercent()).isEqualTo(25);
            assertThat(shop.problem()).isTrue();
        });
    }

    @Test
    void healthIndicatorIsDownWithoutRecentCrawl() {
        Clock now = Clock.fixed(Instant.now(), TimeConfig.ZONE);
        CrawlerHealthIndicator indicator = new CrawlerHealthIndicator(journal, health, now);
        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);

        finished("MOYO", "WASHING_MACHINES", 75, 25);
        Health up = indicator.health();

        assertThat(up.getStatus()).isEqualTo(Status.UP);
        assertThat(up.getDetails().get("shopsWithProblems"))
                .isEqualTo(Map.of("MOYO", Map.of("failedCategories", 0, "errorPercent", 25)));

        CrawlerHealthIndicator later = new CrawlerHealthIndicator(journal, health,
                Clock.offset(now, CrawlerHealthIndicator.STALE_AFTER.plusMinutes(1)));
        assertThat(later.health().getStatus()).isEqualTo(Status.DOWN);
    }

    private void finished(String shop, String category, int saved, int failed) {
        CrawlRun run = journal.start(shop, category, CrawlRun.Trigger.MANUAL, 100);
        Instant at = run.getStartedAt();
        journal.finish(run.getId(), new CrawlReport(shop, category, saved + failed, saved, 0, 0, 0, 0, 0, failed,
                List.of(), at, at));
    }

}
