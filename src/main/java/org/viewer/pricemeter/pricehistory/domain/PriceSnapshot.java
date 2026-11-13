package org.viewer.pricemeter.pricehistory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Інтервал, протягом якого стан ціни пропозиції не змінювався.
 * {@code validFrom} — перше спостереження стану, {@code validTo} — останнє підтвердження.
 */
@Entity
@Table(name = "price_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PriceSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_id", nullable = false, updatable = false)
    private Long offerId;

    @Column(precision = 12, scale = 2, updatable = false)
    private BigDecimal price;

    @Column(name = "old_price", precision = 12, scale = 2, updatable = false)
    private BigDecimal oldPrice;

    @Column(nullable = false, updatable = false)
    private boolean available;

    @Column(name = "valid_from", nullable = false, updatable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    public PriceSnapshot(Long offerId, BigDecimal price, BigDecimal oldPrice, boolean available, Instant observedAt) {
        this.offerId = offerId;
        this.price = price;
        this.oldPrice = oldPrice;
        this.available = available;
        this.validFrom = observedAt;
        this.validTo = observedAt;
    }

    /**
     * Чи збігається стан ціни зі спостереженням (порівняння BigDecimal без урахування масштабу).
     */
    public boolean hasSameState(BigDecimal price, BigDecimal oldPrice, boolean available) {
        return this.available == available
                && sameAmount(this.price, price)
                && sameAmount(this.oldPrice, oldPrice);
    }

    /**
     * Подовжує інтервал до моменту останнього підтвердження стану.
     */
    public void confirmAt(Instant observedAt) {
        if (observedAt.isAfter(validTo)) {
            validTo = observedAt;
        }
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

}
