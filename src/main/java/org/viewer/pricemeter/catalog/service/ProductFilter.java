package org.viewer.pricemeter.catalog.service;

/**
 * Фільтр списку товарів.
 *
 * @param categoryCode  код категорії або порожньо — усі
 * @param query         підрядок назви або коду моделі (код порівнюється без пробілів і роздільників)
 * @param multiShopOnly лише товари, що є в кількох магазинах
 */
public record ProductFilter(String categoryCode, String query, boolean multiShopOnly) {
}
