package org.viewer.pricemeter.catalog.service;

import org.viewer.pricemeter.catalog.domain.MatchStatus;

/**
 * Пропозиція в обсязі, потрібному для зіставлення, разом із поточним рішенням.
 *
 * @param productId поточний товар або {@code null}, якщо ще не зіставлено
 */
public record MatchableOffer(
        long id,
        String shopCode,
        String categoryCode,
        String title,
        String brandRaw,
        String mpn,
        String gtin,
        Long productId,
        MatchStatus matchStatus) {
}
