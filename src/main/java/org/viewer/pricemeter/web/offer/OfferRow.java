package org.viewer.pricemeter.web.offer;

import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Рядок таблиці пропозицій.
 *
 * @param price      поточна ціна; {@code null}, якщо історії ще немає або товар без ціни
 * @param oldPrice   заявлена «стара» ціна
 * @param available  чи є товар у наявності за останнім спостереженням
 * @param lastSeenAt коли пропозицію бачили востаннє (київський час)
 */
public record OfferRow(
        Long id,
        String shopCode,
        String shopName,
        String categoryName,
        String title,
        String url,
        BigDecimal price,
        BigDecimal oldPrice,
        boolean available,
        boolean active,
        LocalDateTime lastSeenAt
) {

    /**
     * @param price останній стан ціни або {@code null}, якщо історії немає; магазин і категорія мають бути завантажені
     */
    public static OfferRow of(Offer offer, PriceSnapshot price) {
        return new OfferRow(
                offer.getId(),
                offer.getShop().getCode(),
                offer.getShop().getName(),
                offer.getCategory() != null ? offer.getCategory().getName() : null,
                offer.getRawTitle(),
                offer.getUrl(),
                price != null ? price.getPrice() : null,
                price != null ? price.getOldPrice() : null,
                price != null && price.isAvailable(),
                offer.isActive(),
                LocalDateTime.ofInstant(offer.getLastSeenAt(), TimeConfig.ZONE));
    }

    /** Ціна, за якою товар можна купити зараз: є ціна й наявність. */
    public boolean purchasable() {
        return available && price != null;
    }

    /** Заявлена знижка у відсотках, округлена до цілого; {@code null}, якщо старої ціни немає. */
    public Integer discountPercent() {
        if (price == null || oldPrice == null || oldPrice.signum() <= 0) {
            return null;
        }
        return BigDecimal.ONE.subtract(price.divide(oldPrice, 4, RoundingMode.HALF_UP))
                .movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValue();
    }

}
