package org.viewer.pricemeter.subscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Надіслане (або спробуване) сповіщення підписнику.
 */
@Entity
@Table(name = "notification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationLog {

    public enum Kind {
        /** Найнижча ціна не вища за бажану. */
        TARGET_REACHED,
        /** Реальне зниження ціни. */
        REAL_DROP
    }

    public enum Channel {
        EMAIL,
        /** Пошту не налаштовано — лише запис у лог застосунку. */
        LOG
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_title", nullable = false, length = 512)
    private String productTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "previous_price", precision = 12, scale = 2)
    private BigDecimal previousPrice;

    @Column(name = "shop_name", nullable = false, length = 128)
    private String shopName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Channel channel;

    @Column(nullable = false)
    private boolean delivered;

    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NotificationLog(Long userId, Long productId, String productTitle, Kind kind, BigDecimal price,
                           BigDecimal previousPrice, String shopName, Channel channel, boolean delivered, String error,
                           Instant createdAt) {
        this.userId = userId;
        this.productId = productId;
        this.productTitle = productTitle;
        this.kind = kind;
        this.price = price;
        this.previousPrice = previousPrice;
        this.shopName = shopName;
        this.channel = channel;
        this.delivered = delivered;
        this.error = error;
        this.createdAt = createdAt;
    }

}
