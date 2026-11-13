package org.viewer.pricemeter.matching;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.viewer.pricemeter.crawler.CrawlCompletedEvent;

/**
 * Зіставляє категорії одразу після обходу, щоб нові пропозиції потрапили в товари того ж дня.
 * Виконується в потоці обходу (обхід і так фоновий); помилка зіставлення обхід не зачіпає.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingOnCrawlListener {

    private final MatchingService matching;

    /** Раніше за сповіщення підписників ({@code SubscriptionNotifier}, {@code @Order(2)}). */
    @EventListener
    @Order(1)
    public void onCrawlCompleted(CrawlCompletedEvent event) {
        try {
            matching.matchCategories(event.categoryCodes());
        } catch (MatchingAlreadyRunningException e) {
            log.info("Зіставлення після обходу {} пропущено: вже виконується", event.categoryCodes());
        } catch (RuntimeException e) {
            log.error("Зіставлення після обходу {} не вдалося", event.categoryCodes(), e);
        }
    }

}
