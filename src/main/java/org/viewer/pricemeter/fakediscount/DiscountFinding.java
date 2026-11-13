package org.viewer.pricemeter.fakediscount;

import org.viewer.pricemeter.analytics.MarketOffer;

/**
 * Пропозиція із заявленою знижкою та її оцінка.
 *
 * @param productId    товар, до якого зіставлено пропозицію; {@code null} — ще не зіставлено
 * @param categoryCode категорія пропозиції
 */
public record DiscountFinding(MarketOffer offer, Long productId, String categoryCode, DiscountAssessment assessment) {
}
