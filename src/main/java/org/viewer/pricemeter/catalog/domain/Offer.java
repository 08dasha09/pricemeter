package org.viewer.pricemeter.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Пропозиція конкретного магазину — сторінка товару в магазині.
 *
 * <p>{@link DynamicUpdate}: краулер і зіставлення змінюють різні поля однієї пропозиції паралельно, тож UPDATE
 * пише лише змінені колонки, щоб не затерти чуже рішення застарілим значенням.
 */
@Entity
@DynamicUpdate
@Table(name = "offer")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shop_id", nullable = false, updatable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /** Артикул товару в магазині. */
    @Column(name = "external_id", nullable = false, updatable = false, length = 128)
    private String externalId;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(name = "raw_title", nullable = false, length = 512)
    private String rawTitle;

    @Column(name = "brand_raw", length = 128)
    private String brandRaw;

    @Column(length = 14)
    private String gtin;

    /** Код моделі виробника, як його вказав магазин. */
    @Column(length = 128)
    private String mpn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> specs;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "match_confidence", precision = 4, scale = 3)
    private BigDecimal matchConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false, length = 16)
    private MatchStatus matchStatus = MatchStatus.PENDING;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    public Offer(Shop shop, String externalId, String url, String rawTitle) {
        this.shop = shop;
        this.externalId = externalId;
        this.url = url;
        this.rawTitle = rawTitle;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (firstSeenAt == null) {
            firstSeenAt = now;
        }
        if (lastSeenAt == null) {
            lastSeenAt = now;
        }
    }

}
