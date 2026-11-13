package org.viewer.pricemeter.fakediscount.rule;

import org.viewer.pricemeter.analytics.MarketOffer;

import java.math.BigDecimal;
import java.util.List;

/**
 * Дані для правил: пропозиція із заявленою знижкою та поточні пропозиції інших магазинів.
 *
 * @param offer       пропозиція в наявності, у якої є стара ціна
 * @param others      пропозиції інших магазинів у наявності (без інших пропозицій того самого магазину)
 * @param otherMedian медіана цін {@code others}; {@code null}, якщо їх немає
 */
public record DiscountContext(MarketOffer offer, List<MarketOffer> others, BigDecimal otherMedian) {

    public boolean hasMarket() {
        return otherMedian != null;
    }

}
