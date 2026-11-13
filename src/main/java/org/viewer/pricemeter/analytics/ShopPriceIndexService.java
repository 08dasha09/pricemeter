package org.viewer.pricemeter.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.service.CatalogService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Індекс цін магазинів: на товарах, які зараз можна купити щонайменше у двох магазинах, — наскільки ціна магазину
 * відрізняється від медіани цін <b>інших</b> магазинів (медіана таких відхилень по товарах) і як часто магазин
 * найдешевший. Від кількох пропозицій одного магазину на товар береться найнижча ціна.
 */
@Service
@RequiredArgsConstructor
public class ShopPriceIndexService {

    private final CatalogService catalog;
    private final ProductMarketService markets;

    /** Загалом і окремо по кожній категорії — одним проходом. */
    public PriceIndexReport report() {
        List<Product> products = catalog.getMultiShopProducts();
        Map<Long, ProductMarket> byProduct = markets.markets(products.stream().map(Product::getId).toList());
        Map<String, List<ProductMarket>> byCategory = new TreeMap<>();
        for (Product product : products) {
            byCategory.computeIfAbsent(product.getCategory().getCode(), k -> new ArrayList<>())
                    .add(byProduct.get(product.getId()));
        }
        Map<String, List<ShopPriceIndex>> categories = new TreeMap<>();
        byCategory.forEach((category, categoryMarkets) -> categories.put(category, compare(categoryMarkets)));
        return new PriceIndexReport(compare(new ArrayList<>(byProduct.values())), categories);
    }

    /** Магазини від найдешевшого (за медіанним відхиленням). */
    static List<ShopPriceIndex> compare(List<ProductMarket> productMarkets) {
        Map<String, Accumulator> byShop = new HashMap<>();
        for (ProductMarket market : productMarkets) {
            Map<String, MarketOffer> cheapestPerShop = market.offers().stream()
                    .filter(MarketOffer::purchasable)
                    .collect(Collectors.toMap(MarketOffer::shopCode, o -> o,
                            (a, b) -> a.price().compareTo(b.price()) <= 0 ? a : b));
            if (cheapestPerShop.size() < 2) {
                continue;
            }
            BigDecimal min = cheapestPerShop.values().stream().map(MarketOffer::price)
                    .min(Comparator.naturalOrder()).orElseThrow();
            cheapestPerShop.forEach((shop, offer) -> {
                List<BigDecimal> others = cheapestPerShop.entrySet().stream()
                        .filter(e -> !e.getKey().equals(shop))
                        .map(e -> e.getValue().price())
                        .toList();
                Accumulator acc = byShop.computeIfAbsent(shop, k -> new Accumulator(offer.shopName()));
                acc.deviations.add(PriceStats.deviationPercent(offer.price(), PriceStats.median(others)));
                if (offer.price().compareTo(min) == 0) {
                    boolean alone = cheapestPerShop.values().stream()
                            .filter(o -> o.price().compareTo(min) == 0).count() == 1;
                    if (alone) {
                        acc.cheapest++;
                    } else {
                        acc.tiedCheapest++;
                    }
                }
            });
        }
        return byShop.entrySet().stream()
                .map(e -> e.getValue().toIndex(e.getKey()))
                .sorted(Comparator.comparingInt(ShopPriceIndex::medianDeviationPercent)
                        .thenComparing(ShopPriceIndex::shopName))
                .toList();
    }

    private static final class Accumulator {
        private final String shopName;
        private final List<Integer> deviations = new ArrayList<>();
        private int cheapest;
        private int tiedCheapest;

        private Accumulator(String shopName) {
            this.shopName = shopName;
        }

        ShopPriceIndex toIndex(String shopCode) {
            List<Integer> sorted = deviations.stream().filter(Objects::nonNull).sorted().toList();
            int n = sorted.size();
            int median = n % 2 == 1 ? sorted.get(n / 2)
                    : Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2f);
            return new ShopPriceIndex(shopCode, shopName, n, median, cheapest, tiedCheapest);
        }
    }

    /**
     * @param overall    усі категорії разом
     * @param byCategory код категорії → магазини
     */
    public record PriceIndexReport(List<ShopPriceIndex> overall, Map<String, List<ShopPriceIndex>> byCategory) {

        public ShopPriceIndex overallOf(String shopCode) {
            return overall.stream().filter(s -> s.shopCode().equals(shopCode)).findFirst().orElse(null);
        }
    }

    /**
     * @param products               на скількох товарах порівняно (товар є в магазині й ще хоча б в одному)
     * @param medianDeviationPercent медіана відхилень ціни магазину від медіани інших магазинів, %: −4 — зазвичай на 4%
     *                               дешевше за інших
     * @param cheapest               на скількох із них магазин найдешевший — сам, без інших за тією самою ціною
     * @param tiedCheapest           на скількох найнижча ціна однакова в нього й ще в когось (часто — рекомендована
     *                               ціна виробника скрізь)
     */
    public record ShopPriceIndex(String shopCode, String shopName, int products, int medianDeviationPercent,
                                 int cheapest, int tiedCheapest) {

        public int cheapestPercent() {
            return percent(cheapest);
        }

        public int tiedCheapestPercent() {
            return percent(tiedCheapest);
        }

        private int percent(int count) {
            return products == 0 ? 0 : Math.round(count * 100f / products);
        }
    }

}
