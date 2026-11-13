package org.viewer.pricemeter.fakediscount.rule;

import org.springframework.stereotype.Component;
import org.viewer.pricemeter.analytics.MarketOffer;
import org.viewer.pricemeter.analytics.PriceStats;

import java.util.Optional;

/**
 * У більшості інших магазинів товар продається за тією самою ціною без жодної акції.
 */
@Component
public class SamePriceElsewhereRule implements DiscountRule {

    public static final String CODE = "SAME_PRICE_ELSEWHERE";
    static final double WEIGHT = 0.3;
    /** «Та сама ціна» — у межах ±2%. */
    static final int SAME_PRICE_PERCENT = 2;

    @Override
    public Optional<RuleHit> evaluate(DiscountContext context) {
        long same = context.others().stream().filter(other -> samePriceWithoutPromo(context.offer(), other)).count();
        int total = context.others().size();
        if (same * 2 <= total) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(CODE, WEIGHT,
                "У " + same + " з " + total + " інших магазинів та сама ціна без акції"));
    }

    private static boolean samePriceWithoutPromo(MarketOffer offer, MarketOffer other) {
        return other.oldPrice() == null
                && Math.abs(PriceStats.deviationPercent(other.price(), offer.price())) <= SAME_PRICE_PERCENT;
    }

}
