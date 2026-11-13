package org.viewer.pricemeter.crawler.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Налаштування магазинів для краулера ({@code crawler.shops.<код>.*}), наприклад:
 * <pre>
 * crawler.shops.moyo.max-pages=30
 * crawler.shops.moyo.categories[WASHING_MACHINES]=/ua/bt/kbt/stiralnie-mashiny/
 * </pre>
 */
@ConfigurationProperties("crawler")
public record CrawlerShopsProperties(Map<String, Shop> shops) {

    public CrawlerShopsProperties {
        shops = shops == null ? Map.of() : Map.copyOf(shops);
    }

    public Shop forShop(String shopCode) {
        return Optional.ofNullable(shops.get(shopCode.toLowerCase(Locale.ROOT))).orElse(Shop.EMPTY);
    }

    /**
     * @param categories          код категорії → шлях до сторінки категорії в магазині
     * @param maxPages            скільки сторінок категорії обходити максимум
     * @param productSitemaps     карти сайту зі сторінками товарів (для магазинів, де категорія рендериться
     *                            в браузері і товарів немає в HTML)
     * @param productSlugPrefixes код категорії → початки слагів товарів цієї категорії в {@code productSitemaps}
     */
    public record Shop(Map<String, String> categories, Integer maxPages, List<String> productSitemaps,
                       Map<String, List<String>> productSlugPrefixes) {

        static final Shop EMPTY = new Shop(Map.of(), null, List.of(), Map.of());

        private static final int DEFAULT_MAX_PAGES = 50;

        public Shop {
            categories = categories == null ? Map.of() : Map.copyOf(categories);
            productSitemaps = productSitemaps == null ? List.of() : List.copyOf(productSitemaps);
            productSlugPrefixes = productSlugPrefixes == null ? Map.of() : Map.copyOf(productSlugPrefixes);
        }

        public int maxPagesOrDefault() {
            return maxPages != null && maxPages > 0 ? maxPages : DEFAULT_MAX_PAGES;
        }

    }

}
