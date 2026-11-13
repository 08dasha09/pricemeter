package org.viewer.pricemeter.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Поточний стан ринку товару: ціни в магазинах, медіана, відхилення кожної пропозиції від неї.
 *
 * <p>Відхилення рахується від медіани всіх наявних пропозицій товару (включно з самою пропозицією) — таблиця
 * читається як «де дешевше за ринок». Ринкова ознака вердикту рахуватиме медіану інших магазинів.
 *
 * <p>Неактивна пропозиція (зникла з сайту) лишається в таблиці, але вважається відсутньою в наявності: її остання
 * ціна застаріла й не входить ні в медіану, ні в найнижчу ціну, ні в оцінки вердикту й знижок.
 */
@Service
@RequiredArgsConstructor
public class ProductMarketService {

    private final CatalogService catalog;
    private final PriceHistoryService priceHistory;

    /** Ринок одного товару; існування товару перевіряє викликач. */
    public ProductMarket market(Long productId) {
        List<Offer> offers = catalog.getOffersOfProduct(productId);
        return build(offers, currentPrices(offers));
    }

    /** «Ринок» з довільного набору пропозицій (напр. пропозиції, ще не зіставленої з товаром). */
    public ProductMarket marketOf(List<Offer> offers) {
        return build(offers, currentPrices(offers));
    }

    /** Ринки кількох товарів трьома запитами; товар без пропозицій — {@link ProductMarket#EMPTY}. */
    public Map<Long, ProductMarket> markets(Collection<Long> productIds) {
        List<Offer> offers = catalog.getOffersOfProducts(productIds);
        Map<Long, PriceSnapshot> prices = currentPrices(offers);
        Map<Long, List<Offer>> byProduct = offers.stream()
                .collect(Collectors.groupingBy(offer -> offer.getProduct().getId()));
        Map<Long, ProductMarket> markets = new HashMap<>();
        for (Long productId : productIds) {
            List<Offer> productOffers = byProduct.get(productId);
            markets.put(productId, productOffers == null ? ProductMarket.EMPTY : build(productOffers, prices));
        }
        return markets;
    }

    private Map<Long, PriceSnapshot> currentPrices(List<Offer> offers) {
        return priceHistory.getCurrentPrices(offers.stream().map(Offer::getId).toList());
    }

    static ProductMarket build(List<Offer> offers, Map<Long, PriceSnapshot> prices) {
        List<BigDecimal> purchasable = offers.stream()
                .filter(offer -> isAvailable(offer, prices.get(offer.getId())))
                .map(offer -> prices.get(offer.getId()).getPrice())
                .filter(Objects::nonNull)
                .toList();
        BigDecimal minPrice = purchasable.stream().min(Comparator.naturalOrder()).orElse(null);
        // з однією ціною медіана — сама ця ціна, відхилення завжди 0: порівнювати немає з чим
        BigDecimal median = purchasable.size() > 1 ? PriceStats.median(purchasable) : null;

        List<MarketOffer> marketOffers = offers.stream()
                .map(offer -> toMarketOffer(offer, prices.get(offer.getId()), median, minPrice, purchasable.size()))
                .sorted(Comparator.comparing((MarketOffer o) -> !o.purchasable())
                        .thenComparing(MarketOffer::price, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MarketOffer::shopName)
                        .thenComparingLong(MarketOffer::id))
                .toList();
        List<ProductMarket.Shop> shops = offers.stream()
                .map(offer -> new ProductMarket.Shop(offer.getShop().getCode(), offer.getShop().getName()))
                .distinct()
                .sorted(Comparator.comparing(ProductMarket.Shop::name))
                .toList();
        return new ProductMarket(marketOffers, minPrice, median, purchasable.size(), shops);
    }

    private static MarketOffer toMarketOffer(Offer offer, PriceSnapshot price, BigDecimal median, BigDecimal minPrice,
                                             int purchasableCount) {
        boolean available = isAvailable(offer, price);
        boolean purchasable = available && price.getPrice() != null;
        return new MarketOffer(
                offer.getId(),
                offer.getShop().getCode(),
                offer.getShop().getName(),
                offer.getRawTitle(),
                offer.getUrl(),
                price != null ? price.getPrice() : null,
                price != null ? price.getOldPrice() : null,
                available,
                offer.isActive(),
                LocalDateTime.ofInstant(offer.getLastSeenAt(), TimeConfig.ZONE),
                purchasable ? PriceStats.deviationPercent(price.getPrice(), median) : null,
                purchasable && purchasableCount > 1 && price.getPrice().compareTo(minPrice) == 0);
    }

    private static boolean isAvailable(Offer offer, PriceSnapshot price) {
        return offer.isActive() && price != null && price.isAvailable();
    }

}
