package org.viewer.pricemeter.fakediscount.rule;

/**
 * Спрацювання правила.
 *
 * @param code        код правила, напр. {@code PROMO_NOT_BELOW_MARKET}
 * @param weight      внесок у підозрілість знижки (0–1)
 * @param explanation пояснення з цифрами українською
 */
public record RuleHit(String code, double weight, String explanation) {
}
