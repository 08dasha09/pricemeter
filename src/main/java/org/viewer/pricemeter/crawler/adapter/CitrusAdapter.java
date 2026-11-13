package org.viewer.pricemeter.crawler.adapter;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.crawler.http.HttpFetcher;
import org.viewer.pricemeter.crawler.parser.JsonLdProductParser;
import org.viewer.pricemeter.crawler.parser.ParsedProduct;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Цитрус (citrus.ua): товар у JSON-LD Product, стара ціна — у {@code div.old-price[data-old-price]}.
 * Сторінки категорії: {@code /<категорія>/page_N/}.
 */
@Slf4j
@Component
public class CitrusAdapter extends AbstractShopAdapter {

    static final URI BASE = URI.create("https://citrus.ua");

    /** Числовий ідентифікатор товару в кінці адреси: {@code ...-780732.html}. */
    private static final Pattern PRODUCT_ID = Pattern.compile("-(\\d+)\\.html$");

    private final JsonLdProductParser jsonLd;

    public CitrusAdapter(HttpFetcher fetcher, CatalogService catalog, CrawlerShopsProperties properties, Clock clock,
                         JsonLdProductParser jsonLd) {
        super(fetcher, catalog, properties, clock);
        this.jsonLd = jsonLd;
    }

    @Override
    public String shopCode() {
        return "CITRUS";
    }

    @Override
    public List<URI> discoverOfferUrls(String categoryCode, int limit) {
        String path = categoryPath(categoryCode);
        List<URI> found = discoverFromCategoryPages(BASE.resolve(path), limit,
                doc -> productLinks(doc, path),
                (doc, pageNo) -> Optional.of(pageUri(path, pageNo + 1)));
        log.info("CITRUS {}: discovered {} offer URLs", categoryCode, found.size());
        return found;
    }

    @Override
    public ScrapedOffer fetchOffer(URI url) {
        Document doc = fetchDocument(url);
        return parseOffer(doc, URI.create(doc.location()), clock.instant());
    }

    // --------------------------------------------- розбір без мережі (тестується на фікстурах)

    static URI pageUri(String categoryPath, int pageNo) {
        return pageNo <= 1 ? BASE.resolve(categoryPath) : BASE.resolve(categoryPath + "page_" + pageNo + "/");
    }

    /**
     * Посилання на товари цієї категорії (шлях починається з шляху категорії). Один товар може мати
     * кілька адрес (українська й російська), тому дублікати відкидаються за ідентифікатором.
     */
    static List<URI> productLinks(Document categoryPage, String categoryPath) {
        String prefix = BASE.resolve(categoryPath).toString();
        Map<String, URI> byId = new LinkedHashMap<>();
        for (Element a : categoryPage.select("a[href$=.html]")) {
            String href = a.absUrl("href");
            if (!href.startsWith(prefix) || href.substring(prefix.length()).contains("/")) {
                continue;
            }
            Matcher m = PRODUCT_ID.matcher(href);
            if (m.find()) {
                byId.putIfAbsent(m.group(1), URI.create(href));
            }
        }
        return new ArrayList<>(byId.values());
    }

    ScrapedOffer parseOffer(Document doc, URI pageUri, Instant scrapedAt) {
        ParsedProduct product = jsonLd.parse(doc, pageUri)
                .orElseThrow(() -> new OfferParseException(pageUri, "No Product JSON-LD"));
        if (product.name() == null) {
            throw new OfferParseException(pageUri, "Product has no name");
        }

        BigDecimal oldPrice = product.oldPrice() != null
                ? product.oldPrice()
                // у картках «схожих товарів» той самий клас стоїть на span, тому беремо саме div
                : priceFromAttribute(doc, "div.old-price[data-old-price]", "data-old-price");

        return new ScrapedOffer(
                externalId(product, pageUri),
                pageUri,
                product.name(),
                product.brand(),
                product.gtin(),
                realMpn(product),
                product.price(),
                oldPrice,
                product.currency(),
                product.availability(),
                product.specs(),
                scrapedAt);
    }

    private static String externalId(ParsedProduct product, URI pageUri) {
        Matcher m = PRODUCT_ID.matcher(pageUri.getPath());
        if (m.find()) {
            return m.group(1);
        }
        if (product.sku() != null) {
            return product.sku();
        }
        throw new OfferParseException(pageUri, "Cannot determine Citrus product id");
    }

    /** Цитрус кладе в {@code mpn} власний числовий id товару — це не код моделі. */
    private static String realMpn(ParsedProduct product) {
        String mpn = product.mpn();
        if (mpn == null || mpn.equals(product.sku()) || mpn.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return mpn;
    }

}
