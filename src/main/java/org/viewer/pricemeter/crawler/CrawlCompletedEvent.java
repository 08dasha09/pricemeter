package org.viewer.pricemeter.crawler;

import java.util.Set;

/**
 * Обхід завершено — пропозиції цих категорій могли з'явитися або змінитися. Публікується і після збою
 * обходу: частину пропозицій до збою вже збережено.
 *
 * @param categoryCodes категорії, які обходились
 */
public record CrawlCompletedEvent(Set<String> categoryCodes) {

    public CrawlCompletedEvent {
        categoryCodes = Set.copyOf(categoryCodes);
    }

}
