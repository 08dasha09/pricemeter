package org.viewer.pricemeter.fakediscount.rule;

import java.util.Optional;

/**
 * Ознака недостовірної знижки. Правило бачить лише пропозиції в наявності із заявленою старою ціною.
 */
public interface DiscountRule {

    Optional<RuleHit> evaluate(DiscountContext context);

    /** Міжмагазинне правило потребує ринку (інших магазинів з товаром у наявності). */
    default boolean requiresMarket() {
        return true;
    }

}
