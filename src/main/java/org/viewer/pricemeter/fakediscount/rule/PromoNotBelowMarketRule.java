package org.viewer.pricemeter.fakediscount.rule;

import org.springframework.stereotype.Component;
import org.viewer.pricemeter.analytics.MarketPosition;
import org.viewer.pricemeter.analytics.PriceStats;

import java.util.Optional;

/**
 * Акційна ціна не нижча за ринкову медіану (у межах ±5% або вище) — реальної економії немає, хоч знижку й заявлено.
 * Головна міжмагазинна ознака: вигідність визначає відхилення кінцевої ціни від ринку, а не глибина знижки.
 */
@Component
public class PromoNotBelowMarketRule implements DiscountRule {

    public static final String CODE = "PROMO_NOT_BELOW_MARKET";
    static final double WEIGHT = 0.5;

    @Override
    public Optional<RuleHit> evaluate(DiscountContext context) {
        int deviation = PriceStats.deviationPercent(context.offer().price(), context.otherMedian());
        if (deviation < -MarketPosition.TOLERANCE_PERCENT) {
            return Optional.empty();
        }
        String explanation = deviation > MarketPosition.TOLERANCE_PERCENT
                ? "Акційна ціна на " + deviation + "% вища за медіану інших магазинів"
                : "Акційна ціна на рівні інших магазинів (" + (deviation > 0 ? "+" : "") + deviation
                        + "% від медіани) — реальної економії немає";
        return Optional.of(new RuleHit(CODE, WEIGHT, explanation));
    }

}
