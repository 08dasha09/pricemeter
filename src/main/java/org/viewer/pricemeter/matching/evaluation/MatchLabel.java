package org.viewer.pricemeter.matching.evaluation;

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
import org.viewer.pricemeter.matching.similarity.MatchScore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Ручна мітка пари пропозицій: той самий товар чи різні. Пара впорядкована ({@code offerAId < offerBId}).
 * Пропозиції зберігаються числами — модуль не залежить від сутностей каталогу.
 */
@Entity
@Table(name = "match_label")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchLabel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "offer_a_id", nullable = false, updatable = false)
    private long offerAId;

    @Column(name = "offer_b_id", nullable = false, updatable = false)
    private long offerBId;

    @Column(name = "category_code", nullable = false, updatable = false, length = 64)
    private String categoryCode;

    @Column(name = "same_product", nullable = false)
    private boolean sameProduct;

    /** Оцінка на момент розмітки. */
    @Column(nullable = false, precision = 4, scale = 3)
    private BigDecimal score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MatchScore.Reason reason;

    @Column(name = "labeled_at", nullable = false)
    private Instant labeledAt;

    public MatchLabel(OfferPair pair, String categoryCode, boolean sameProduct, MatchScore score, Instant labeledAt) {
        this.offerAId = pair.first();
        this.offerBId = pair.second();
        this.categoryCode = categoryCode;
        relabel(sameProduct, score, labeledAt);
    }

    /** Повторна розмітка тієї самої пари — нове рішення замінює попереднє. */
    public void relabel(boolean sameProduct, MatchScore score, Instant labeledAt) {
        this.sameProduct = sameProduct;
        this.score = BigDecimal.valueOf(score.value()).setScale(3, RoundingMode.HALF_UP);
        this.reason = score.reason();
        this.labeledAt = labeledAt;
    }

    public OfferPair pair() {
        return new OfferPair(offerAId, offerBId);
    }

}
