package org.viewer.pricemeter.crawler.adapter;

import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Адаптер інтернет-магазину: знаходить сторінки товарів у категоріях і розбирає їх.
 */
public interface ShopAdapter {

    /** Код магазину, як у таблиці {@code shop} (MOYO, CITRUS, ...). */
    String shopCode();

    /** Коди категорій, для яких відомі адреси в магазині. */
    Set<String> supportedCategories();

    /**
     * Адреси сторінок товарів категорії.
     *
     * @param limit максимальна кількість адрес
     * @throws IllegalArgumentException якщо категорія не підтримується
     */
    List<URI> discoverOfferUrls(String categoryCode, int limit);

    /**
     * Отримує та розбирає сторінку товару.
     *
     * @throws org.viewer.pricemeter.crawler.http.FetchException якщо сторінку не вдалося отримати
     * @throws OfferParseException                               якщо на сторінці немає даних товару
     */
    ScrapedOffer fetchOffer(URI url);

}
