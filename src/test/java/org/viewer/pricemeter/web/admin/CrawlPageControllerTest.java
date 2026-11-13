package org.viewer.pricemeter.web.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ExtendedModelMap;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.crawler.scheduling.CrawlAlreadyRunningException;
import org.viewer.pricemeter.crawler.scheduling.CrawlHealthService;
import org.viewer.pricemeter.crawler.scheduling.CrawlJournal;
import org.viewer.pricemeter.crawler.scheduling.CrawlLauncher;
import org.viewer.pricemeter.crawler.scheduling.CrawlRun;
import org.viewer.pricemeter.crawler.scheduling.CrawlScheduler;
import org.viewer.pricemeter.web.security.SecurityConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CrawlPageController.class)
@WithMockUser(roles = "ADMIN")
@Import(SecurityConfig.class)
class CrawlPageControllerTest {

    private static final Instant T0 = Instant.parse("2026-10-06T17:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CrawlLauncher launcher;

    @MockitoBean
    private CrawlJournal journal;

    @MockitoBean
    private CrawlHealthService crawlHealth;

    @MockitoBean
    private CatalogService catalog;

    @BeforeEach
    void setUp() {
        when(catalog.getEnabledShops()).thenReturn(List.of(
                new Shop("MOYO", "Moyo", "https://www.moyo.ua"),
                new Shop("CITRUS", "Цитрус", "https://citrus.ua")));
        when(catalog.getCategories()).thenReturn(List.of(
                new Category("WASHING_MACHINES", "Пральні машини"),
                new Category("MICROWAVES", "Мікрохвильові печі")));
        when(launcher.targets()).thenReturn(new TreeMap<>(Map.of(
                "MOYO", List.of("WASHING_MACHINES"),
                "CITRUS", List.of("MICROWAVES", "WASHING_MACHINES"))));
    }

    @Test
    void rendersFormAndJournal() throws Exception {
        CrawlRun finished = run(2, "MOYO", "WASHING_MACHINES", CrawlRun.Status.FINISHED, CrawlRun.Trigger.SCHEDULED);
        when(finished.getDuration()).thenReturn(Duration.ofSeconds(75));
        when(finished.getDiscovered()).thenReturn(20);
        when(finished.getFailed()).thenReturn(1);
        when(finished.getErrors()).thenReturn(List.of("https://www.moyo.ua/x — HTTP 500"));
        CrawlRun failed = run(1, "CITRUS", "MICROWAVES", CrawlRun.Status.FAILED, CrawlRun.Trigger.MANUAL);
        when(failed.getError()).thenReturn("robots.txt unavailable");
        CrawlRun interrupted = run(0, "CITRUS", "WASHING_MACHINES", CrawlRun.Status.INTERRUPTED,
                CrawlRun.Trigger.SCHEDULED);
        when(journal.recent(anyInt())).thenReturn(List.of(finished, failed, interrupted));

        mvc.perform(get("/admin/crawl"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("value=\"CITRUS:MICROWAVES\""),
                        containsString("Цитрус — Мікрохвильові печі"),
                        containsString("value=\"MOYO:WASHING_MACHINES\""),
                        not(containsString("MOYO:MICROWAVES")),
                        containsString("06.10 20:00:00"),
                        containsString("75 с"),
                        containsString("розклад"),
                        containsString("вручну"),
                        containsString("перервано"),
                        containsString("https://www.moyo.ua/x — HTTP 500"),
                        containsString("robots.txt unavailable"),
                        // у тесті немає біна розкладу
                        containsString("Розклад вимкнено"),
                        containsString("Обійти всі зараз (по 200)"),
                        not(containsString("location.reload")))));
    }

    @Test
    void showsNextScheduledRun() {
        CrawlScheduler scheduler = mock(CrawlScheduler.class);
        when(scheduler.nextRun()).thenReturn(ZonedDateTime.of(2026, 10, 7, 4, 0, 0, 0, TimeConfig.ZONE));
        when(scheduler.limitPerCategory()).thenReturn(200);
        CrawlPageController controller = new CrawlPageController(launcher, journal, crawlHealth, catalog,
                new StaticListableBeanFactory(
                        Map.of("crawlScheduler", scheduler)).getBeanProvider(CrawlScheduler.class));
        ExtendedModelMap model = new ExtendedModelMap();

        controller.page(model);

        assertThat(model)
                .containsEntry("nextScheduledRun", LocalDateTime.of(2026, 10, 7, 4, 0))
                .containsEntry("scheduledLimit", 200)
                .containsEntry("fullCrawlLimit", 200);
    }

    @Test
    void warnsAboutShopsWithManyErrors() throws Exception {
        when(crawlHealth.shops()).thenReturn(List.of(
                new CrawlHealthService.ShopCrawlHealth("MOYO", null, 4, 0, 1, 1),
                new CrawlHealthService.ShopCrawlHealth("EPICENTR", null, 5, 1, 120, 23)));

        mvc.perform(get("/admin/crawl"))
                .andExpect(content().string(allOf(
                        containsString("категорій зі збоєм — 1;"),
                        containsString("помилок у 23% сторінок товарів"),
                        not(containsString("помилок у 1% сторінок")))));
    }

    @Test
    void reloadsWhileCrawlIsRunning() throws Exception {
        CrawlRun running = run(1, "MOYO", "WASHING_MACHINES", CrawlRun.Status.RUNNING, CrawlRun.Trigger.MANUAL);
        when(journal.recent(anyInt())).thenReturn(List.of(running));

        mvc.perform(get("/admin/crawl"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("триває"), containsString("location.reload"))));
    }

    @Test
    void launchesCrawlAndRedirects() throws Exception {
        CrawlRun run = run(7, "MOYO", "WASHING_MACHINES", CrawlRun.Status.RUNNING, CrawlRun.Trigger.MANUAL);
        when(launcher.launch("MOYO", "WASHING_MACHINES", 15)).thenReturn(run);

        mvc.perform(post("/admin/crawl").with(csrf()).param("target", "MOYO:WASHING_MACHINES").param("limit", "15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/crawl"))
                .andExpect(flash().attribute("message", "Обхід #7 запущено"));
    }

    @Test
    void launchesCrawlOfAllShops() throws Exception {
        mvc.perform(post("/admin/crawl/all").with(csrf()).param("limit", "20"))
                .andExpect(redirectedUrl("/admin/crawl"))
                .andExpect(flash().attribute("refreshSoon", true));

        verify(launcher).launchAll(CrawlRun.Trigger.MANUAL, 20);
    }

    @Test
    void reportsInvalidInputAndConflicts() throws Exception {
        mvc.perform(post("/admin/crawl").with(csrf()).param("target", "MOYO").param("limit", "5"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/crawl").with(csrf()).param("target", "MOYO:WASHING_MACHINES").param("limit", "0"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(post("/admin/crawl/all").with(csrf()).param("limit", "100000"))
                .andExpect(flash().attributeExists("error"));
        verify(launcher, never()).launch(anyString(), anyString(), anyInt());
        verify(launcher, never()).launchAll(any(), anyInt());

        when(launcher.launch("MOYO", "MICROWAVES", 5)).thenThrow(NotFoundException.of("Category", "MICROWAVES"));
        mvc.perform(post("/admin/crawl").with(csrf()).param("target", "MOYO:MICROWAVES").param("limit", "5"))
                .andExpect(flash().attribute("error", "Цей магазин не вміє обходити таку категорію"));

        when(launcher.launch("MOYO", "WASHING_MACHINES", 5))
                .thenThrow(new CrawlAlreadyRunningException("MOYO", "WASHING_MACHINES"));
        mvc.perform(post("/admin/crawl").with(csrf()).param("target", "MOYO:WASHING_MACHINES").param("limit", "5"))
                .andExpect(flash().attribute("error", "Обхід цієї категорії магазину вже триває"));
    }

    private static CrawlRun run(long id, String shop, String category, CrawlRun.Status status,
                                CrawlRun.Trigger trigger) {
        CrawlRun run = mock(CrawlRun.class);
        when(run.getId()).thenReturn(id);
        when(run.getShopCode()).thenReturn(shop);
        when(run.getCategoryCode()).thenReturn(category);
        when(run.getTrigger()).thenReturn(trigger);
        when(run.getLimit()).thenReturn(20);
        when(run.getStartedAt()).thenReturn(T0);
        when(run.getStatus()).thenReturn(status);
        when(run.isRunning()).thenReturn(status == CrawlRun.Status.RUNNING);
        return run;
    }

}
