package org.viewer.pricemeter.crawler.adapter;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.crawler.http.HttpFetcher;
import org.viewer.pricemeter.crawler.parser.MicrodataProductParser;
import org.viewer.pricemeter.crawler.parser.ParsedProduct;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Moyo (moyo.ua): товар у мікророзмітці Schema.org, стара ціна — у {@code .js-old-price[data-price]}.
 * Обходимо українську версію сайту ({@code /ua/...}).
 */
@Slf4j
@Component
public class MoyoAdapter extends AbstractShopAdapter {

    static final URI BASE = URI.create("https://www.moyo.ua");

    /** {@code https://www.moyo.ua/ua/<slug>/<id>.html} */
    private static final Pattern PRODUCT_URL = Pattern.compile("^https://www\\.moyo\\.ua/ua/[^/]+/(\\d+)\\.html$");

    private final MicrodataProductParser microdata;

    public MoyoAdapter(HttpFetcher fetcher, CatalogService catalog, CrawlerShopsProperties properties, Clock clock,
                       MicrodataProductParser microdata) {
        super(fetcher, catalog, properties, clock);
        this.microdata = microdata;
    }

    @Override
    public String shopCode() {
        return "MOYO";
    }

    @Override
    public List<URI> discoverOfferUrls(String categoryCode, int limit) {
        List<URI> found = discoverFromCategoryPages(BASE.resolve(categoryPath(categoryCode)), limit,
                MoyoAdapter::productLinks, (doc, pageNo) -> nextPage(doc));
        log.info("MOYO {}: discovered {} offer URLs", categoryCode, found.size());
        return found;
    }

    @Override
    public ScrapedOffer fetchOffer(URI url) {
        Document doc = fetchDocument(url);
        return parseOffer(doc, URI.create(doc.location()), clock.instant());
    }

    // --------------------------------------------- розбір без мережі (тестується на фікстурах)

    /** Посилання на товари з карток каталогу (без блоків «рекомендуємо» тощо). */
    static List<URI> productLinks(Document categoryPage) {
        Set<URI> links = new LinkedHashSet<>();
        for (Element a : categoryPage.select(".product-card a.product-card_image[href]")) {
            String href = a.absUrl("href");
            if (PRODUCT_URL.matcher(href).matches()) {
                links.add(URI.create(href));
            }
        }
        return new ArrayList<>(links);
    }

    static Optional<URI> nextPage(Document categoryPage) {
        Element next = categoryPage.selectFirst("link[rel=next][href]");
        return next == null ? Optional.empty() : Optional.of(URI.create(next.absUrl("href")));
    }

    ScrapedOffer parseOffer(Document doc, URI pageUri, Instant scrapedAt) {
        ParsedProduct product = microdata.parse(doc, pageUri)
                .orElseThrow(() -> new OfferParseException(pageUri, "No Product microdata"));
        if (product.name() == null) {
            throw new OfferParseException(pageUri, "Product has no name");
        }

        BigDecimal oldPrice = product.oldPrice() != null
                ? product.oldPrice()
                : priceFromAttribute(doc, ".js-old-price[data-price]", "data-price");

        return new ScrapedOffer(
                externalId(doc, pageUri),
                pageUri,
                product.name(),
                product.brand(),
                product.gtin(),
                product.mpn(),
                product.price(),
                oldPrice,
                product.currency(),
                product.availability(),
                product.specs(),
                scrapedAt);
    }

    /** Ідентифікатор товару: {@code #main-product[data-id]}, інакше число з URL. */
    private static String externalId(Document doc, URI pageUri) {
        Element main = doc.selectFirst("#main-product[data-id]");
        if (main != null && !main.attr("data-id").isBlank()) {
            return main.attr("data-id").strip();
        }
        Matcher m = PRODUCT_URL.matcher(pageUri.toString());
        if (m.matches()) {
            return m.group(1);
        }
        throw new OfferParseException(pageUri, "Cannot determine Moyo product id");
    }

}
