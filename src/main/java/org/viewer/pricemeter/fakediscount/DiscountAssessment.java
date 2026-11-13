package org.viewer.pricemeter.fakediscount;

import org.viewer.pricemeter.fakediscount.rule.RuleHit;

import java.math.BigDecimal;
import java.util.List;

/**
 * Оцінка заявленої знижки пропозиції.
 *
 * @param level                   підсумок
 * @param declaredPercent         глибина знижки, як її заявив магазин (від старої ціни), %
 * @param marketDeviationPercent  відхилення акційної ціни від медіани інших магазинів, %; {@code null} — ринку немає.
 *                                Від'ємне — дешевше за ринок: це і є реальна економія
 * @param otherMedian             медіана цін інших магазинів
 * @param score                   сума ваг спрацьованих правил
 * @param hits                    спрацьовані правила з поясненнями
 */
public record DiscountAssessment(
        Level level,
        Integer declaredPercent,
        Integer marketDeviationPercent,
        BigDecimal otherMedian,
        double score,
        List<RuleHit> hits) {

    public static final DiscountAssessment NO_DISCOUNT =
            new DiscountAssessment(Level.NO_DISCOUNT, null, null, null, 0, List.of());

    public enum Level {
        /** Знижку не заявлено (немає старої ціни) або товару немає в наявності. */
        NO_DISCOUNT,
        /** Знижку заявлено, але перевірити нема з чим: інших магазинів з товаром немає, історії замало. */
        UNKNOWN,
        /** Жодна ознака не спрацювала. */
        GENUINE,
        /** Є ознаки недостовірності. */
        SUSPICIOUS,
        /** Кілька сильних ознак — знижка, найімовірніше, штучна. */
        FAKE
    }

}
