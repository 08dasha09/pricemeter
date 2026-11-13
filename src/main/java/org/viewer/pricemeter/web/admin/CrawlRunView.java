package org.viewer.pricemeter.web.admin;

import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.crawler.scheduling.CrawlRun;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Рядок таблиці запусків обходу.
 *
 * @param startedAt       початок (київський час)
 * @param durationSeconds тривалість; {@code null}, поки обхід триває або якщо його перервано
 * @param errors          перші помилки окремих товарів
 * @param error           причина збою всього обходу
 */
public record CrawlRunView(
        long id,
        boolean scheduled,
        String shopName,
        String categoryName,
        int limit,
        LocalDateTime startedAt,
        CrawlRun.Status status,
        Long durationSeconds,
        Integer discovered,
        Integer saved,
        Integer created,
        Integer priceChanges,
        Integer refreshed,
        Integer skipped,
        Integer deactivated,
        Integer failed,
        List<String> errors,
        String error
) {

    static CrawlRunView of(CrawlRun run, String shopName, String categoryName) {
        Duration duration = run.getDuration();
        return new CrawlRunView(run.getId(), run.getTrigger() == CrawlRun.Trigger.SCHEDULED, shopName, categoryName,
                run.getLimit(), LocalDateTime.ofInstant(run.getStartedAt(), TimeConfig.ZONE), run.getStatus(),
                duration != null ? duration.toSeconds() : null,
                run.getDiscovered(), run.getSaved(), run.getCreated(), run.getPriceChanges(), run.getRefreshed(),
                run.getSkipped(), run.getDeactivated(), run.getFailed(),
                run.getErrors() != null ? run.getErrors() : List.of(), run.getError());
    }

    public boolean finished() {
        return status == CrawlRun.Status.FINISHED;
    }

}
