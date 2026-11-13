package org.viewer.pricemeter.api.dto;

import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.fakediscount.rule.RuleHit;

import java.math.BigDecimal;
import java.util.List;

/**
 * Оцінка заявленої знижки пропозиції.
 *
 * @param marketDeviationPercent відхилення акційної ціни від медіани інших магазинів (від'ємне — реальна економія)
 */
public record DiscountDto(
        long offerId,
        DiscountAssessment.Level level,
        Integer declaredPercent,
        Integer marketDeviationPercent,
        BigDecimal otherMedian,
        double score,
        List<RuleHit> hits) {

    public static DiscountDto of(long offerId, DiscountAssessment a) {
        return new DiscountDto(offerId, a.level(), a.declaredPercent(), a.marketDeviationPercent(), a.otherMedian(),
                Math.round(a.score() * 100) / 100.0, a.hits());
    }

}
