package org.viewer.pricemeter.subscription;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.analytics.Verdict;
import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.subscription.domain.NotificationLog;
import org.viewer.pricemeter.subscription.service.NotificationRule;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRuleTest {

    private static final Instant NOW = Instant.parse("2026-10-19T02:00:00Z");
    private static final BigDecimal REFERENCE = new BigDecimal("20000");

    @Test
    void targetReachedWhenPriceDropsToDesiredLevel() {
        assertThat(decide("15000", "15000", REFERENCE, null, Verdict.Level.USUAL, null))
                .contains(NotificationLog.Kind.TARGET_REACHED);
        // нижча за опорну, але ще вище бажаної й вердикт «звичайна» — мовчимо
        assertThat(decide("19000", "15000", REFERENCE, null, Verdict.Level.USUAL, null)).isEmpty();
    }

    @Test
    void realDropNeedsDepthLowVerdictAndTrustworthyDiscount() {
        assertThat(decide("19000", null, REFERENCE, null, Verdict.Level.BELOW_USUAL, null))
                .contains(NotificationLog.Kind.REAL_DROP);
        assertThat(decide("19000", null, REFERENCE, null, Verdict.Level.BELOW_USUAL, DiscountAssessment.Level.GENUINE))
                .contains(NotificationLog.Kind.REAL_DROP);
        // < 3% — коливання
        assertThat(decide("19500", null, REFERENCE, null, Verdict.Level.BELOW_USUAL, null)).isEmpty();
        // ціна «звичайна» для магазину й ринку
        assertThat(decide("19000", null, REFERENCE, null, Verdict.Level.USUAL, null)).isEmpty();
        // знижка фіктивна чи підозріла — не реальне зниження
        assertThat(decide("19000", null, REFERENCE, null, Verdict.Level.BELOW_USUAL, DiscountAssessment.Level.FAKE))
                .isEmpty();
        assertThat(decide("19000", null, REFERENCE, null, Verdict.Level.BELOW_USUAL,
                DiscountAssessment.Level.SUSPICIOUS)).isEmpty();
    }

    @Test
    void onlyNewLowerPriceAndNotMoreThanDaily() {
        // не нижча за опорну (ту саму ціну вже повідомили) — навіть якщо досягнуто бажаної
        assertThat(decide("15000", "16000", new BigDecimal("15000"), null, Verdict.Level.BELOW_USUAL, null)).isEmpty();
        // минуло менше доби від попереднього сповіщення
        assertThat(decide("15000", "16000", REFERENCE, NOW.minus(Duration.ofHours(23)), Verdict.Level.BELOW_USUAL, null))
                .isEmpty();
        assertThat(decide("15000", "16000", REFERENCE, NOW.minus(Duration.ofHours(24)), Verdict.Level.BELOW_USUAL, null))
                .contains(NotificationLog.Kind.TARGET_REACHED);
    }

    @Test
    void nothingWithoutPriceAndNoDropWithoutReference() {
        assertThat(decide(null, "15000", REFERENCE, null, Verdict.Level.BELOW_USUAL, null)).isEmpty();
        // опорної ще немає: бажану ціну перевіряємо, «зниження» — ні
        assertThat(decide("15000", "16000", null, null, Verdict.Level.BELOW_USUAL, null))
                .contains(NotificationLog.Kind.TARGET_REACHED);
        assertThat(decide("15000", null, null, null, Verdict.Level.BELOW_USUAL, null)).isEmpty();
    }

    private static Optional<NotificationLog.Kind> decide(String price, String target, BigDecimal reference,
                                                         Instant lastNotifiedAt, Verdict.Level verdict,
                                                         DiscountAssessment.Level discount) {
        return NotificationRule.decide(price != null ? new BigDecimal(price) : null,
                target != null ? new BigDecimal(target) : null, reference, lastNotifiedAt, verdict, discount, NOW);
    }

}
