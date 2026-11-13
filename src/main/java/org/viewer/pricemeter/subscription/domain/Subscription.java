package org.viewer.pricemeter.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Підписка користувача на товар. Користувач і товар — ідентифікатори (інші модулі), без JPA-зв'язків.
 */
@Entity
@Table(name = "subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Setter
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Бажана ціна; {@code null} — сповіщати лише про реальне зниження. */
    @Setter
    @Column(name = "target_price", precision = 12, scale = 2)
    private BigDecimal targetPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Setter
    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    /**
     * Опорна ціна: найнижча ціна на момент підписки або останнього сповіщення — сповіщаємо лише про нижчу.
     * {@code null} — товару тоді не було в наявності (заповниться під час наступної перевірки).
     */
    @Setter
    @Column(name = "last_notified_price", precision = 12, scale = 2)
    private BigDecimal lastNotifiedPrice;

    public Subscription(Long userId, Long productId, BigDecimal targetPrice, Instant createdAt) {
        this.userId = userId;
        this.productId = productId;
        this.targetPrice = targetPrice;
        this.createdAt = createdAt;
    }

}
