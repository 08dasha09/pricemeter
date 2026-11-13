package org.viewer.pricemeter.catalog.service;

import org.viewer.pricemeter.catalog.domain.MatchStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * Товар і пропозиції, які до нього належать, за результатом зіставлення.
 *
 * @param productId наявний товар, який зберігається, або {@code null} — створити новий
 * @param offers    пропозиції товару з рішенням щодо кожної
 */
public record ProductAssignment(
        Long productId,
        String title,
        String brand,
        String modelCode,
        List<OfferMatch> offers) {

    /**
     * @param confidence впевненість зіставлення; для {@link MatchStatus#MANUAL} не змінюється
     */
    public record OfferMatch(long offerId, MatchStatus status, BigDecimal confidence) {
    }

}
