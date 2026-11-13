package org.viewer.pricemeter.matching.similarity;

/**
 * Дані пропозиції, потрібні для зіставлення, — як їх зберіг краулер. {@code brandRaw}, {@code mpn},
 * {@code gtin} можуть бути порожніми.
 */
public record OfferText(
        long offerId,
        String shopCode,
        String categoryCode,
        String title,
        String brandRaw,
        String mpn,
        String gtin) {
}
