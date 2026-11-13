package org.viewer.pricemeter.pricehistory.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Одне спостереження ціни пропозиції під час обходу магазину.
 *
 * @param price      поточна ціна; може бути {@code null}, лише якщо товару немає в наявності
 * @param oldPrice   заявлена магазином «стара» (закреслена) ціна, може бути {@code null}
 * @param available  чи є товар у наявності
 * @param observedAt момент спостереження
 */
public record PriceObservation(BigDecimal price, BigDecimal oldPrice, boolean available, Instant observedAt) {

    public PriceObservation {
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        if (price == null && available) {
            throw new IllegalArgumentException("price is required when the offer is available");
        }
        requirePositive(price, "price");
        requirePositive(oldPrice, "oldPrice");
        // «стара» ціна, не вища за поточну, не є знижкою — це шум розмітки магазину
        if (oldPrice != null && (price == null || oldPrice.compareTo(price) <= 0)) {
            oldPrice = null;
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

}
