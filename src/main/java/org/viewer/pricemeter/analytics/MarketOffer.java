package org.viewer.pricemeter.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Поточна пропозиція магазину на товар у порівнянні з рештою ринку.
 *
 * @param price            поточна ціна; {@code null}, якщо історії ще немає
 * @param available        чи є в наявності за останнім спостереженням (неактивна пропозиція — ні)
 * @param active           чи пропозиція ще є на сайті магазину
 * @param lastSeenAt       коли пропозицію бачили востаннє (київський час)
 * @param deviationPercent відхилення від медіани поточних цін товару, %; {@code null} — порівнювати немає з чим
 * @param cheapest         найнижча ціна серед магазинів, де товар є в наявності (якщо таких кілька)
 */
public record MarketOffer(
        long id,
        String shopCode,
        String shopName,
        String title,
        String url,
        BigDecimal price,
        BigDecimal oldPrice,
        boolean available,
        boolean active,
        LocalDateTime lastSeenAt,
        Integer deviationPercent,
        boolean cheapest) {

    /** Ціна, за якою товар можна купити зараз. */
    public boolean purchasable() {
        return available && price != null;
    }

    public MarketPosition position() {
        return MarketPosition.of(deviationPercent);
    }

    /** Заявлена знижка у відсотках, округлена до цілого; {@code null}, якщо старої ціни немає. */
    public Integer discountPercent() {
        if (price == null || oldPrice == null || oldPrice.signum() <= 0) {
            return null;
        }
        return BigDecimal.ONE.subtract(price.divide(oldPrice, 4, RoundingMode.HALF_UP))
                .movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    /** Підпис відхилення зі знаком: {@code "+12%"}, {@code "−9%"} (типографський мінус), {@code "0%"}. */
    public String deviationLabel() {
        if (deviationPercent == null) {
            return null;
        }
        if (deviationPercent > 0) {
            return "+" + deviationPercent + "%";
        }
        return deviationPercent < 0 ? "−" + -deviationPercent + "%" : "0%";
    }

}
