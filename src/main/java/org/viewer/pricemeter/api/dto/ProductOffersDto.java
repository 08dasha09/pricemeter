package org.viewer.pricemeter.api.dto;

import org.viewer.pricemeter.analytics.MarketOffer;
import org.viewer.pricemeter.analytics.MarketPosition;
import org.viewer.pricemeter.analytics.ProductMarket;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Поточні пропозиції товару в магазинах.
 *
 * @param median медіана наявних цін, якщо їх ≥ 2
 * @param offers у наявності — від найдешевшої, без наявності — в кінці
 */
public record ProductOffersDto(Long productId, BigDecimal minPrice, BigDecimal median, List<Offer> offers) {

    public static ProductOffersDto of(Long productId, ProductMarket market) {
        return new ProductOffersDto(productId, market.minPrice(), market.median(),
                market.offers().stream().map(Offer::of).toList());
    }

    /**
     * @param deviationPercent відхилення від медіани, %
     * @param position         положення відносно медіани (±5% — {@code AT_MARKET})
     * @param lastSeenAt       коли пропозицію бачили востаннє (київський час)
     */
    public record Offer(
            long offerId,
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
            MarketPosition position,
            boolean cheapest) {

        static Offer of(MarketOffer o) {
            return new Offer(o.id(), o.shopCode(), o.shopName(), o.title(), o.url(), o.price(), o.oldPrice(),
                    o.available(), o.active(), o.lastSeenAt(), o.deviationPercent(), o.position(), o.cheapest());
        }
    }

}
