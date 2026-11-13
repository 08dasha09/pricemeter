package org.viewer.pricemeter.crawler.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.crawler.CrawlReport;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Журнал запусків обходу ({@code crawl_run}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CrawlJournal {

    private final CrawlRunRepository runs;
    private final Clock clock;

    /**
     * Реєструє початок обходу.
     *
     * @throws CrawlAlreadyRunningException обхід цієї категорії магазину вже триває
     */
    @Transactional
    public CrawlRun start(String shopCode, String categoryCode, CrawlRun.Trigger trigger, int limit) {
        if (runs.existsByShopCodeAndCategoryCodeAndStatus(shopCode, categoryCode, CrawlRun.Status.RUNNING)) {
            throw new CrawlAlreadyRunningException(shopCode, categoryCode);
        }
        try {
            return runs.saveAndFlush(new CrawlRun(shopCode, categoryCode, trigger, limit, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // інший потік встиг зареєструвати запуск тієї ж категорії (унікальний індекс uq_crawl_run_running)
            throw new CrawlAlreadyRunningException(shopCode, categoryCode);
        }
    }

    @Transactional
    public void finish(Long runId, CrawlReport report) {
        get(runId).finish(report);
    }

    @Transactional
    public void fail(Long runId, String error) {
        get(runId).fail(error, clock.instant());
    }

    /** Останні запуски, новіші першими. */
    public List<CrawlRun> recent(int limit) {
        return runs.findAllByOrderByStartedAtDescIdDesc(PageRequest.of(0, limit));
    }

    /** Коли почався останній успішний запуск (будь-якої категорії). */
    public Optional<Instant> lastFinishedStart() {
        return Optional.ofNullable(runs.findLastFinishedStart());
    }

    public boolean isRunning(String shopCode, String categoryCode) {
        return runs.existsByShopCodeAndCategoryCodeAndStatus(shopCode, categoryCode, CrawlRun.Status.RUNNING);
    }

    /**
     * Запуски, що «триватимуть» після перезапуску застосунку, насправді перервані: обходи живуть
     * лише в потоках цього процесу.
     */
    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedOnStartup() {
        int interrupted = runs.markRunningAsInterrupted();
        if (interrupted > 0) {
            log.warn("Marked {} unfinished crawl run(s) as interrupted", interrupted);
        }
    }

    private CrawlRun get(Long runId) {
        return runs.findById(runId).orElseThrow(() -> NotFoundException.of("Crawl run", runId));
    }

}
