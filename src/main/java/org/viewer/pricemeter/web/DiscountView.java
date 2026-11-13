package org.viewer.pricemeter.web;

import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.fakediscount.rule.RuleHit;

import java.util.List;

/**
 * Оцінка знижки для шаблону.
 *
 * @param tone    {@code good} / {@code neutral} / {@code warn} / {@code bad}
 * @param label   коротко: «знижка справжня», «підозріла знижка»…
 * @param reasons пояснення спрацьованих правил (для GENUINE — чому знижка справжня)
 */
public record DiscountView(String tone, String label, List<String> reasons) {

    /** @return {@code null}, якщо знижку не заявлено — показувати нічого */
    public static DiscountView of(DiscountAssessment assessment) {
        return switch (assessment.level()) {
            case NO_DISCOUNT -> null;
            case UNKNOWN -> new DiscountView("neutral", "знижку не перевірити",
                    List.of("Інших магазинів з цим товаром у наявності немає — порівняти нема з чим"));
            case GENUINE -> new DiscountView("good", "знижка справжня",
                    List.of("Акційна ціна на " + -assessment.marketDeviationPercent()
                            + "% нижча за медіану інших магазинів"));
            case SUSPICIOUS -> new DiscountView("warn", "підозріла знижка", reasons(assessment));
            case FAKE -> new DiscountView("bad", "фіктивна знижка", reasons(assessment));
        };
    }

    /** Пояснення одним рядком — для підказки. */
    public String reasonsText() {
        return String.join(". ", reasons);
    }

    private static List<String> reasons(DiscountAssessment assessment) {
        return assessment.hits().stream().map(RuleHit::explanation).toList();
    }

}
