package org.viewer.pricemeter.subscription.service;

import org.viewer.pricemeter.analytics.Verdict;
import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.subscription.domain.NotificationLog;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Чи сповіщати підписника про поточну найнижчу ціну товару.
 *
 * <ul>
 *   <li>Сповіщаємо лише про ціну, нижчу за опорну (на момент підписки або попереднього сповіщення), і не частіше
 *       {@link #MIN_INTERVAL}.</li>
 *   <li>{@code TARGET_REACHED} — ціна не вища за бажану.</li>
 *   <li>{@code REAL_DROP} — ціна нижча за опорну щонайменше на {@link #MIN_DROP}, вердикт «нижча за звичайну» (за
 *       історією магазину або ринком), а знижка магазину не фіктивна й не підозріла.</li>
 * </ul>
 */
public final class NotificationRule {

    public static final Duration MIN_INTERVAL = Duration.ofHours(24);
    /** Менше — коливання ціни, а не зниження, про яке варто писати. */
    public static final BigDecimal MIN_DROP = new BigDecimal("0.03");

    private static final Set<DiscountAssessment.Level> UNTRUSTWORTHY =
            Set.of(DiscountAssessment.Level.FAKE, DiscountAssessment.Level.SUSPICIOUS);

    private NotificationRule() {
    }

    /**
     * @param price          поточна найнижча ціна в наявності; {@code null} — ніде немає
     * @param referencePrice опорна ціна; {@code null} — ще не відома
     * @param verdict        вердикт пропозиції з найнижчою ціною
     * @param discount       оцінка її заявленої знижки; {@code null} — знижки немає
     */
    public static Optional<NotificationLog.Kind> decide(BigDecimal price, BigDecimal targetPrice,
                                                        BigDecimal referencePrice, Instant lastNotifiedAt,
                                                        Verdict.Level verdict, DiscountAssessment.Level discount,
                                                        Instant now) {
        if (price == null) {
            return Optional.empty();
        }
        if (lastNotifiedAt != null && Duration.between(lastNotifiedAt, now).compareTo(MIN_INTERVAL) < 0) {
            return Optional.empty();
        }
        if (referencePrice != null && price.compareTo(referencePrice) >= 0) {
            return Optional.empty();
        }
        if (targetPrice != null && price.compareTo(targetPrice) <= 0) {
            return Optional.of(NotificationLog.Kind.TARGET_REACHED);
        }
        boolean deepEnough = referencePrice != null
                && referencePrice.subtract(price).compareTo(referencePrice.multiply(MIN_DROP)) >= 0;
        boolean trustworthy = discount == null || !UNTRUSTWORTHY.contains(discount);
        if (deepEnough && verdict == Verdict.Level.BELOW_USUAL && trustworthy) {
            return Optional.of(NotificationLog.Kind.REAL_DROP);
        }
        return Optional.empty();
    }

}
