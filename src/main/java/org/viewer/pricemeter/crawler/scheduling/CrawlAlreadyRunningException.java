package org.viewer.pricemeter.crawler.scheduling;

/**
 * Обхід цієї категорії магазину вже триває.
 */
public class CrawlAlreadyRunningException extends IllegalStateException {

    public CrawlAlreadyRunningException(String shopCode, String categoryCode) {
        super("Crawl of " + shopCode + " " + categoryCode + " is already running");
    }

}
