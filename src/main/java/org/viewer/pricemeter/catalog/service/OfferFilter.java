package org.viewer.pricemeter.catalog.service;

/**
 * Фільтр списку пропозицій; порожні поля не обмежують вибірку.
 *
 * @param shopCode     код магазину
 * @param categoryCode код категорії
 * @param query        підрядок назви або коду моделі (без урахування регістру)
 * @param activeOnly   лише активні пропозиції
 */
public record OfferFilter(String shopCode, String categoryCode, String query, boolean activeOnly) {

    public static OfferFilter all() {
        return new OfferFilter(null, null, null, false);
    }

}
