package org.viewer.pricemeter.fakediscount;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.analytics.MarketOffer;
import org.viewer.pricemeter.analytics.PriceStats;
import org.viewer.pricemeter.analytics.ProductMarket;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.fakediscount.rule.DiscountContext;
import org.viewer.pricemeter.fakediscount.rule.DiscountRule;
import org.viewer.pricemeter.fakediscount.rule.RuleHit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Оцінює заявлені знижки за набором правил {@link DiscountRule}: сума ваг спрацьованих правил
 * ≥ {@link #FAKE_SCORE} — FAKE, більше нуля — SUSPICIOUS, нуль — GENUINE.
 *
 * <p>Поки що працюють лише міжмагазинні правила: без інших магазинів з товаром у наявності знижку не перевірити —
 * UNKNOWN (історичні правила з'являться, коли накопичиться історія).
 */
@Service
@RequiredArgsConstructor
public class FakeDiscountDetector {

    /** Два сильні збіги (0.5 + 0.3 і більше) — штучна знижка; один — підозріла (стартовий поріг). */
    static final double FAKE_SCORE = 0.8;
    /** «Фіктивна» — лише якщо ринок — щонайменше два інші магазини; з одним — не більше ніж «підозріла». */
    static final int MIN_SHOPS_FOR_FAKE = 2;

    private final List<DiscountRule> rules;
    private final ProductMarketService markets;

    /**
     * Оцінка знижки однієї пропозиції (магазин і товар мають бути завантажені). Пропозиція без товару —
     * лише з власною ціною: знижку, якщо її заявлено, перевірити нема з чим.
     */
    public DiscountAssessment assessOffer(Offer offer) {
        if (offer.getProduct() != null) {
            return assessProduct(offer.getProduct().getId())
                    .getOrDefault(offer.getId(), DiscountAssessment.NO_DISCOUNT);
        }
        return markets.marketOf(List.of(offer)).offers().stream()
                .findFirst()
                .map(self -> assess(self, List.of(self)))
                .orElse(DiscountAssessment.NO_DISCOUNT);
    }

    /** Оцінки знижок усіх пропозицій товару (ключ — id пропозиції). */
    public Map<Long, DiscountAssessment> assessProduct(Long productId) {
        return assess(markets.market(productId));
    }

    public Map<Long, DiscountAssessment> assess(ProductMarket market) {
        Map<Long, DiscountAssessment> result = new LinkedHashMap<>();
        for (MarketOffer offer : market.offers()) {
            result.put(offer.id(), assess(offer, market.offers()));
        }
        return result;
    }

    DiscountAssessment assess(MarketOffer offer, List<MarketOffer> allOffers) {
        if (!offer.purchasable() || offer.oldPrice() == null) {
            return DiscountAssessment.NO_DISCOUNT;
        }
        List<MarketOffer> others = allOffers.stream()
                .filter(o -> o.purchasable() && !o.shopCode().equals(offer.shopCode()))
                .toList();
        BigDecimal median = PriceStats.median(others.stream().map(MarketOffer::price).toList());
        DiscountContext context = new DiscountContext(offer, others, median);

        List<RuleHit> hits = new ArrayList<>();
        boolean anyRuleApplicable = false;
        for (DiscountRule rule : rules) {
            if (rule.requiresMarket() && !context.hasMarket()) {
                continue;
            }
            anyRuleApplicable = true;
            rule.evaluate(context).ifPresent(hits::add);
        }
        hits.sort(Comparator.comparingDouble(RuleHit::weight).reversed());
        double score = hits.stream().mapToDouble(RuleHit::weight).sum();
        DiscountAssessment.Level level;
        if (!anyRuleApplicable) {
            level = DiscountAssessment.Level.UNKNOWN;
        } else if (score >= FAKE_SCORE - 1e-9 && otherShops(others) >= MIN_SHOPS_FOR_FAKE) {
            level = DiscountAssessment.Level.FAKE;
        } else {
            level = score > 0 ? DiscountAssessment.Level.SUSPICIOUS : DiscountAssessment.Level.GENUINE;
        }
        return new DiscountAssessment(level, offer.discountPercent(),
                PriceStats.deviationPercent(offer.price(), median), median, score, List.copyOf(hits));
    }

    private static long otherShops(List<MarketOffer> others) {
        return others.stream().map(MarketOffer::shopCode).distinct().count();
    }

}
