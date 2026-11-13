package org.viewer.pricemeter.crawler.scheduling;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface CrawlRunRepository extends JpaRepository<CrawlRun, Long> {

    List<CrawlRun> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);

    /** Початок останнього успішного запуску; {@code null} — таких ще не було. */
    @Query("""
            select max(r.startedAt) from CrawlRun r
            where r.status = org.viewer.pricemeter.crawler.scheduling.CrawlRun.Status.FINISHED
            """)
    Instant findLastFinishedStart();

    /** Останній завершений (успішно чи зі збоєм) запуск кожної категорії кожного магазину. */
    @Query(value = """
            select distinct on (shop_code, category_code) * from crawl_run
            where status in ('FINISHED', 'FAILED')
            order by shop_code, category_code, started_at desc, id desc
            """, nativeQuery = true)
    List<CrawlRun> findLatestCompletedPerCategory();

    boolean existsByShopCodeAndCategoryCodeAndStatus(String shopCode, String categoryCode, CrawlRun.Status status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CrawlRun r set r.status = org.viewer.pricemeter.crawler.scheduling.CrawlRun.Status.INTERRUPTED
            where r.status = org.viewer.pricemeter.crawler.scheduling.CrawlRun.Status.RUNNING
            """)
    int markRunningAsInterrupted();

}
