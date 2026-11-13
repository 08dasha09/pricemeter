package org.viewer.pricemeter.fakediscount.rule;

import org.springframework.stereotype.Component;
import org.viewer.pricemeter.analytics.PriceStats;

import java.util.Optional;

/**
 * Заявлена «стара» ціна суттєво вища за ринкову медіану — базову ціну завищено, щоб знижка виглядала глибшою.
 */
@Component
public class InflatedOldPriceRule implements DiscountRule {

    public static final String CODE = "INFLATED_OLD_PRICE";
    static final double WEIGHT = 0.4;
    /** На скільки відсотків стара ціна має перевищувати медіану (стартовий поріг). */
    static final int THRESHOLD_PERCENT = 15;

    @Override
    public Optional<RuleHit> evaluate(DiscountContext context) {
        int excess = PriceStats.deviationPercent(context.offer().oldPrice(), context.otherMedian());
        if (excess < THRESHOLD_PERCENT) {
            return Optional.empty();
        }
        return Optional.of(new RuleHit(CODE, WEIGHT,
                "Заявлена стара ціна на " + excess + "% вища за медіану інших магазинів"));
    }

}
