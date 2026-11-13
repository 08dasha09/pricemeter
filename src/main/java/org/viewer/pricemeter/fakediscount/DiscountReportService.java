package org.viewer.pricemeter.fakediscount;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.analytics.MarketOffer;
import org.viewer.pricemeter.analytics.ProductMarket;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Усі поточні заявлені знижки з оцінками та статистика по магазинах. Рахується на льоту: кілька запитів на всі
 * пропозиції зі знижкою (сотні), без збереження результатів.
 */
@Service
@RequiredArgsConstructor
public class DiscountReportService {

    private static final Map<DiscountAssessment.Level, Integer> SEVERITY = Map.of(
            DiscountAssessment.Level.FAKE, 0,
            DiscountAssessment.Level.SUSPICIOUS, 1,
            DiscountAssessment.Level.GENUINE, 2,
            DiscountAssessment.Level.UNKNOWN, 3,
            DiscountAssessment.Level.NO_DISCOUNT, 4);

    private final CatalogService catalog;
    private final PriceHistoryService priceHistory;
    private final ProductMarketService markets;
    private final FakeDiscountDetector detector;

    /**
     * Пропозиції в наявності із заявленою знижкою: спершу фіктивні, далі підозрілі (від найвищої оцінки), справжні,
     * неперевірні; у межах рівня — від найглибшої заявленої знижки.
     */
    public List<DiscountFinding> findings() {
        List<Offer> offers = catalog.getOffers(priceHistory.getOfferIdsWithCurrentDiscount());
        Set<Long> discounted = offers.stream().map(Offer::getId).collect(Collectors.toSet());
        Map<Long, Offer> byId = offers.stream().collect(Collectors.toMap(Offer::getId, Function.identity()));

        List<DiscountFinding> findings = new ArrayList<>();
        Set<Long> productIds = new HashSet<>();
        List<Offer> unmatched = new ArrayList<>();
        for (Offer offer : offers) {
            if (offer.getProduct() != null) {
                productIds.add(offer.getProduct().getId());
            } else {
                unmatched.add(offer);
            }
        }
        for (Map.Entry<Long, ProductMarket> entry : markets.markets(productIds).entrySet()) {
            ProductMarket market = entry.getValue();
            detector.assess(market).forEach((offerId, assessment) -> {
                if (discounted.contains(offerId)) {
                    findings.add(finding(market, offerId, entry.getKey(), byId.get(offerId), assessment));
                }
            });
        }
        // ще не зіставлені — кожна сама по собі: ринку немає
        for (MarketOffer offer : markets.marketOf(unmatched).offers()) {
            findings.add(new DiscountFinding(offer, null, categoryOf(byId.get(offer.id())),
                    detector.assess(offer, List.of(offer))));
        }

        findings.removeIf(f -> f.assessment().level() == DiscountAssessment.Level.NO_DISCOUNT);
        findings.sort(Comparator
                .comparing((DiscountFinding f) -> SEVERITY.get(f.assessment().level()))
                .thenComparing(f -> -f.assessment().score())
                .thenComparing(f -> -f.assessment().declaredPercent())
                .thenComparingLong(f -> f.offer().id()));
        return findings;
    }

    /** Статистика по магазинах — від найбільшої частки недостовірних знижок. */
    public List<ShopDiscountStats> shopStats(List<DiscountFinding> findings) {
        Map<String, List<DiscountFinding>> byShop = new LinkedHashMap<>();
        findings.forEach(f -> byShop.computeIfAbsent(f.offer().shopCode(), s -> new ArrayList<>()).add(f));
        return byShop.values().stream()
                .map(DiscountReportService::stats)
                .sorted(Comparator.comparing((ShopDiscountStats s) -> s.untrustworthyPercent() == null)
                        .thenComparing(s -> s.untrustworthyPercent() == null ? 0 : -s.untrustworthyPercent())
                        .thenComparing(ShopDiscountStats::shopName))
                .toList();
    }

    private static ShopDiscountStats stats(List<DiscountFinding> findings) {
        MarketOffer first = findings.getFirst().offer();
        int genuine = count(findings, DiscountAssessment.Level.GENUINE);
        int suspicious = count(findings, DiscountAssessment.Level.SUSPICIOUS);
        int fake = count(findings, DiscountAssessment.Level.FAKE);
        return new ShopDiscountStats(first.shopCode(), first.shopName(), findings.size(),
                genuine + suspicious + fake, genuine, suspicious, fake);
    }

    private static int count(List<DiscountFinding> findings, DiscountAssessment.Level level) {
        return (int) findings.stream().filter(f -> f.assessment().level() == level).count();
    }

    private static DiscountFinding finding(ProductMarket market, Long offerId, Long productId, Offer offer,
                                           DiscountAssessment assessment) {
        MarketOffer marketOffer = market.offers().stream().filter(o -> o.id() == offerId).findFirst().orElseThrow();
        return new DiscountFinding(marketOffer, productId, categoryOf(offer), assessment);
    }

    private static String categoryOf(Offer offer) {
        return offer != null && offer.getCategory() != null ? offer.getCategory().getCode() : null;
    }

}
