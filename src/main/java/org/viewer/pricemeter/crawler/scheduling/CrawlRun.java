package org.viewer.pricemeter.crawler.scheduling;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.viewer.pricemeter.crawler.CrawlReport;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Запис журналу обходів: один запуск обходу категорії магазину.
 */
@Entity
@Table(name = "crawl_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlRun {

    public enum Status {
        RUNNING,
        FINISHED,
        /** Обхід упав цілком (напр. не вдалося отримати сторінки категорії). */
        FAILED,
        /** Застосунок зупинився посеред обходу. */
        INTERRUPTED
    }

    public enum Trigger { MANUAL, SCHEDULED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_code", nullable = false, updatable = false, length = 32)
    private String shopCode;

    @Column(name = "category_code", nullable = false, updatable = false, length = 64)
    private String categoryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, updatable = false, length = 16)
    private Trigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "item_limit", nullable = false, updatable = false)
    private int limit;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private Integer discovered;
    private Integer saved;
    private Integer created;

    @Column(name = "price_changes")
    private Integer priceChanges;

    private Integer refreshed;
    private Integer skipped;
    private Integer deactivated;
    private Integer failed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> errors;

    private String error;

    public CrawlRun(String shopCode, String categoryCode, Trigger trigger, int limit, Instant startedAt) {
        this.shopCode = shopCode;
        this.categoryCode = categoryCode;
        this.trigger = trigger;
        this.limit = limit;
        this.startedAt = startedAt;
        this.status = Status.RUNNING;
    }

    void finish(CrawlReport report) {
        status = Status.FINISHED;
        finishedAt = report.finishedAt();
        discovered = report.discovered();
        saved = report.saved();
        created = report.created();
        priceChanges = report.priceChanges();
        refreshed = report.refreshed();
        skipped = report.skipped();
        deactivated = report.deactivated();
        failed = report.failed();
        errors = report.errors().isEmpty() ? null : List.copyOf(report.errors());
    }

    void fail(String error, Instant at) {
        status = Status.FAILED;
        finishedAt = at;
        this.error = error;
    }

    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    /** Тривалість; {@code null}, поки обхід триває або якщо його перервано. */
    public Duration getDuration() {
        return finishedAt == null ? null : Duration.between(startedAt, finishedAt);
    }

}
