package org.viewer.pricemeter.crawler.scheduling;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.crawler.CrawlCompletedEvent;
import org.viewer.pricemeter.crawler.CrawlReport;
import org.viewer.pricemeter.crawler.CrawlService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Обходи виконуються в інших потоках, тож тест працює без спільної транзакції й чистить журнал сам.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({TestcontainersConfiguration.class, TimeConfig.class, CatalogService.class, CrawlJournal.class})
class CrawlLauncherTest {

    private static final Instant T0 = Instant.parse("2026-10-07T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-07T02:00:00Z");

    @MockitoBean
    private CrawlService crawlService;

    @Autowired
    private CrawlJournal journal;

    @Autowired
    private CrawlRunRepository runs;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private OfferRepository offers;

    /** Фонові завдання виконуються лише за викликом {@link #runPending()}. */
    private final Queue<Runnable> pending = new ArrayDeque<>();
    private final List<Object> events = new CopyOnWriteArrayList<>();
    private CrawlLauncher launcher;

    @BeforeEach
    void setUp() {
        runs.deleteAll();
        offers.deleteAll();
        when(crawlService.supportedCategories()).thenReturn(Map.of(
                "MOYO", List.of("REFRIGERATORS", "WASHING_MACHINES"),
                "CITRUS", List.of("WASHING_MACHINES"),
                // магазин вимкнено в БД — не обходимо, навіть якщо адаптер є
                "ROZETKA", List.of("WASHING_MACHINES")));
        when(crawlService.crawlCategory(anyString(), anyString(), anyInt()))
                .thenAnswer(inv -> report(inv.getArgument(0), inv.getArgument(1), 3));
        launcher = new CrawlLauncher(crawlService, journal, catalog, Clock.fixed(NOW, TimeConfig.ZONE),
                Duration.ofDays(7), pending::add, events::add);
    }

    @Test
    void manualRunIsJournaledAndFinishesInBackground() {
        CrawlRun run = launcher.launch(" moyo ", "washing_machines", 5);

        CrawlRun started = runs.findById(run.getId()).orElseThrow();
        assertThat(started.getStatus()).isEqualTo(CrawlRun.Status.RUNNING);
        assertThat(started.getTrigger()).isEqualTo(CrawlRun.Trigger.MANUAL);
        assertThat(started.getShopCode()).isEqualTo("MOYO");
        assertThat(started.getLimit()).isEqualTo(5);
        assertThat(events).isEmpty();

        runPending();

        assertThat(events).containsExactly(new CrawlCompletedEvent(Set.of("WASHING_MACHINES")));

        CrawlRun finished = runs.findById(run.getId()).orElseThrow();
        assertThat(finished.getStatus()).isEqualTo(CrawlRun.Status.FINISHED);
        assertThat(finished.getDiscovered()).isEqualTo(3);
        assertThat(finished.getPriceChanges()).isEqualTo(1);
        assertThat(finished.getErrors()).containsExactly("https://shop/broken — HTTP 500");
        assertThat(finished.getFinishedAt()).isEqualTo(T0.plusSeconds(60));
    }

    @Test
    void failureOfWholeCrawlIsJournaled() {
        when(crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 5))
                .thenThrow(new IllegalStateException("category page layout changed"));

        CrawlRun run = launcher.launch("MOYO", "WASHING_MACHINES", 5);
        runPending();

        CrawlRun failed = runs.findById(run.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(CrawlRun.Status.FAILED);
        assertThat(failed.getError()).isEqualTo("category page layout changed");
        assertThat(failed.getFinishedAt()).isNotNull();
        // до збою частину пропозицій могло бути збережено
        assertThat(events).containsExactly(new CrawlCompletedEvent(Set.of("WASHING_MACHINES")));
    }

    @Test
    void rejectsUnsupportedOrDisabledTargets() {
        assertThatThrownBy(() -> launcher.launch("CITRUS", "REFRIGERATORS", 5))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> launcher.launch("ROZETKA", "WASHING_MACHINES", 5))
                .isInstanceOf(NotFoundException.class);
        assertThat(runs.count()).isZero();
    }

    @Test
    void rejectsSecondRunOfSameTargetWhileFirstIsRunning() {
        launcher.launch("MOYO", "WASHING_MACHINES", 5);

        assertThatThrownBy(() -> launcher.launch("MOYO", "WASHING_MACHINES", 10))
                .isInstanceOf(CrawlAlreadyRunningException.class);
        launcher.launch("MOYO", "REFRIGERATORS", 5);
        launcher.launch("CITRUS", "WASHING_MACHINES", 5);

        runPending();
        launcher.launch("MOYO", "WASHING_MACHINES", 10);
        assertThat(journal.recent(10)).hasSize(4);
    }

    @Test
    void crawlNowReturnsReportAndJournalsFailures() {
        CrawlReport report = launcher.crawlNow("citrus", "washing_machines", 7);

        assertThat(report.shopCode()).isEqualTo("CITRUS");
        assertThat(journal.recent(1).getFirst().getStatus()).isEqualTo(CrawlRun.Status.FINISHED);

        when(crawlService.crawlCategory("CITRUS", "WASHING_MACHINES", 7)).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> launcher.crawlNow("CITRUS", "WASHING_MACHINES", 7))
                .hasMessage("boom");
        assertThat(journal.recent(1).getFirst().getStatus()).isEqualTo(CrawlRun.Status.FAILED);
        assertThat(events).hasSize(2);
    }

    @Test
    void crawlsAllEnabledShopsInParallelAndCategoriesSequentially() throws Exception {
        // обхід Moyo чекає, доки почнеться Цитрус: якби магазини йшли послідовно, тест би завис
        CountDownLatch citrusStarted = new CountDownLatch(1);
        Map<String, Integer> concurrentPerShop = new ConcurrentHashMap<>();
        Map<String, Integer> maxConcurrentPerShop = new ConcurrentHashMap<>();
        when(crawlService.crawlCategory(anyString(), anyString(), anyInt())).thenAnswer(inv -> {
            String shop = inv.getArgument(0);
            int now = concurrentPerShop.merge(shop, 1, Integer::sum);
            maxConcurrentPerShop.merge(shop, now, Math::max);
            if (shop.equals("CITRUS")) {
                citrusStarted.countDown();
            } else {
                assertThat(citrusStarted.await(10, TimeUnit.SECONDS)).isTrue();
            }
            concurrentPerShop.merge(shop, -1, Integer::sum);
            return report(shop, inv.getArgument(1), 1);
        });

        int executed = launcher.crawlAll(CrawlRun.Trigger.SCHEDULED, 200);

        assertThat(executed).isEqualTo(3);
        assertThat(journal.recent(10))
                .allSatisfy(run -> {
                    assertThat(run.getStatus()).isEqualTo(CrawlRun.Status.FINISHED);
                    assertThat(run.getTrigger()).isEqualTo(CrawlRun.Trigger.SCHEDULED);
                    assertThat(run.getLimit()).isEqualTo(200);
                })
                .extracting(run -> run.getShopCode() + ":" + run.getCategoryCode())
                .containsExactlyInAnyOrder("MOYO:REFRIGERATORS", "MOYO:WASHING_MACHINES", "CITRUS:WASHING_MACHINES");
        assertThat(maxConcurrentPerShop).containsEntry("MOYO", 1);
        // одна подія на весь обхід
        assertThat(events).containsExactly(new CrawlCompletedEvent(Set.of("REFRIGERATORS", "WASHING_MACHINES")));
        verify(crawlService, never()).crawlCategory("ROZETKA", "WASHING_MACHINES", 200);
    }

    @Test
    void crawlAllSkipsTargetsThatAreAlreadyRunning() {
        launcher.launch("MOYO", "WASHING_MACHINES", 5);

        int executed = launcher.crawlAll(CrawlRun.Trigger.SCHEDULED, 200);

        assertThat(executed).isEqualTo(2);
        verify(crawlService, never()).crawlCategory("MOYO", "WASHING_MACHINES", 200);
    }

    @Test
    void crawlAllDeactivatesOffersNotSeenForDaysOnlyInShopsWithoutFailures() {
        upsert("MOYO", "M-OLD", NOW.minus(Duration.ofDays(8)));
        upsert("MOYO", "M-RECENT", NOW.minus(Duration.ofDays(6)));
        upsert("CITRUS", "C-OLD", NOW.minus(Duration.ofDays(8)));
        when(crawlService.crawlCategory("CITRUS", "WASHING_MACHINES", 50))
                .thenThrow(new IllegalStateException("layout changed"));

        launcher.crawlAll(CrawlRun.Trigger.SCHEDULED, 50);

        assertThat(active("M-OLD")).isFalse();
        assertThat(active("M-RECENT")).isTrue();
        // обхід Цитрусу впав — «не бачили» нічого не означає
        assertThat(active("C-OLD")).isTrue();
    }

    @Test
    void marksUnfinishedRunsAsInterruptedOnStartup() {
        CrawlRun run = launcher.launch("MOYO", "WASHING_MACHINES", 5);
        pending.clear(); // «застосунок зупинився»

        journal.markInterruptedOnStartup();

        assertThat(runs.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(CrawlRun.Status.INTERRUPTED);
        assertThat(journal.isRunning("MOYO", "WASHING_MACHINES")).isFalse();
    }

    private void upsert(String shop, String externalId, Instant seenAt) {
        catalog.upsertOffer(new OfferUpsertCommand(shop, "WASHING_MACHINES", externalId,
                "https://shop/" + externalId, "Product " + externalId, null, null, null, seenAt));
    }

    private boolean active(String externalId) {
        return offers.findAll().stream().filter(o -> o.getExternalId().equals(externalId))
                .findFirst().orElseThrow().isActive();
    }

    private void runPending() {
        while (!pending.isEmpty()) {
            pending.poll().run();
        }
    }

    private static CrawlReport report(String shop, String category, int discovered) {
        return new CrawlReport(shop, category, discovered, discovered - 1, 1, 1, 0, 0, 0, 1,
                List.of("https://shop/broken — HTTP 500"), T0, T0.plusSeconds(60));
    }

}
