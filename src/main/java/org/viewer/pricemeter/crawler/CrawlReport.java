package org.viewer.pricemeter.crawler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Підсумок обходу категорії магазину.
 *
 * @param shopCode     магазин
 * @param categoryCode категорія
 * @param discovered   знайдено адрес товарів
 * @param saved        успішно збережено пропозицій (знайдених у категорії та оновлених за відомою адресою)
 * @param created      з них нових пропозицій
 * @param priceChanges з них зі зміною стану ціни (новий інтервал в історії)
 * @param refreshed    з них відомих пропозицій, яких не було серед знайдених, оновлених за збереженою адресою
 * @param skipped      свідомо пропущено (напр. товар з іншої категорії)
 * @param deactivated  пропозицій, сторінки яких зникли (404 / 410), — позначено неактивними
 * @param failed       помилок
 * @param errors       перші повідомлення про помилки
 * @param startedAt    початок
 * @param finishedAt   завершення
 */
public record CrawlReport(
        String shopCode,
        String categoryCode,
        int discovered,
        int saved,
        int created,
        int priceChanges,
        int refreshed,
        int skipped,
        int deactivated,
        int failed,
        List<String> errors,
        Instant startedAt,
        Instant finishedAt
) {

    public Duration duration() {
        return Duration.between(startedAt, finishedAt);
    }

}
