package org.viewer.pricemeter.crawler.adapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.crawler.http.FetchedPage;
import org.viewer.pricemeter.crawler.http.HttpFetcher;
import org.viewer.pricemeter.crawler.parser.PriceText;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Спільна частина адаптерів: завантаження сторінок з паузою магазину ({@code shop.crawl_delay_ms})
 * та адреси категорій з конфігурації.
 */
public abstract class AbstractShopAdapter implements ShopAdapter {

    protected final HttpFetcher fetcher;
    protected final Clock clock;
    private final CatalogService catalog;
    private final CrawlerShopsProperties.Shop settings;

    protected AbstractShopAdapter(HttpFetcher fetcher, CatalogService catalog, CrawlerShopsProperties properties,
                                  Clock clock) {
        this.fetcher = fetcher;
        this.catalog = catalog;
        this.clock = clock;
        this.settings = properties.forShop(shopCode());
    }

    @Override
    public Set<String> supportedCategories() {
        return settings.categories().keySet();
    }

    protected String categoryPath(String categoryCode) {
        String path = settings.categories().get(categoryCode);
        if (path == null) {
            throw new IllegalArgumentException(shopCode() + " does not support category " + categoryCode);
        }
        return path;
    }

    protected int maxPages() {
        return settings.maxPagesOrDefault();
    }

    protected CrawlerShopsProperties.Shop settings() {
        return settings;
    }

    /**
     * Обходить сторінки категорії, доки не набереться {@code limit} адрес, не скінчаться сторінки
     * або сторінка не перестане давати нові товари.
     *
     * @param firstPage    перша сторінка категорії
     * @param productLinks адреси товарів на сторінці
     * @param nextPage     наступна сторінка за поточною і її номером (1, 2, ...)
     */
    protected List<URI> discoverFromCategoryPages(URI firstPage, int limit,
                                                  Function<Document, List<URI>> productLinks,
                                                  BiFunction<Document, Integer, Optional<URI>> nextPage) {
        return discoverFromCategoryPages(firstPage, limit, productLinks, nextPage, true);
    }

    /**
     * @param stopOnPageWithoutNewLinks зупинитися, якщо сторінка не дала нових товарів; {@code false} —
     *                                  для сторінок, що перетинаються (напр. фільтри за брендами)
     */
    protected List<URI> discoverFromCategoryPages(URI firstPage, int limit,
                                                  Function<Document, List<URI>> productLinks,
                                                  BiFunction<Document, Integer, Optional<URI>> nextPage,
                                                  boolean stopOnPageWithoutNewLinks) {
        Set<URI> found = new LinkedHashSet<>();
        URI page = firstPage;
        for (int pageNo = 1; page != null && pageNo <= maxPages() && found.size() < limit; pageNo++) {
            Document doc = fetchDocument(page);
            int before = found.size();
            for (URI uri : productLinks.apply(doc)) {
                if (found.size() >= limit) {
                    break;
                }
                found.add(uri);
            }
            if (stopOnPageWithoutNewLinks && found.size() == before) {
                break; // сторінка без нових товарів — далі шукати нема чого
            }
            page = nextPage.apply(doc, pageNo).orElse(null);
        }
        return new ArrayList<>(found);
    }

    /** Завантажує сторінку з паузою, налаштованою для магазину, і розбирає її. */
    protected Document fetchDocument(URI uri) {
        FetchedPage page = fetcher.fetch(uri, crawlDelay());
        return Jsoup.parse(page.body(), page.uri().toString());
    }

    /** Завантажує XML (напр. карту сайту) з паузою магазину. */
    protected Document fetchXmlDocument(URI uri) {
        FetchedPage page = fetcher.fetch(uri, crawlDelay());
        return Jsoup.parse(page.body(), page.uri().toString(), Parser.xmlParser());
    }

    private Duration crawlDelay() {
        return Duration.ofMillis(catalog.getShop(shopCode()).getCrawlDelayMs());
    }

    /**
     * Ціна з атрибута першого елемента за селектором, напр. стара ціна {@code .js-old-price[data-price]}.
     *
     * @return ціна або {@code null}, якщо елемента немає чи атрибут не містить ціни
     */
    protected static BigDecimal priceFromAttribute(Document doc, String cssSelector, String attribute) {
        Element element = doc.selectFirst(cssSelector);
        return element == null ? null : PriceText.parse(element.attr(attribute));
    }

}
