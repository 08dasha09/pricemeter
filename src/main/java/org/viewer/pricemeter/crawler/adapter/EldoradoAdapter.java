package org.viewer.pricemeter.crawler.adapter;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.crawler.http.HttpFetcher;
import org.viewer.pricemeter.crawler.parser.PriceText;
import org.viewer.pricemeter.crawler.parser.SchemaOrg;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Eldorado (eldorado.ua): категорії рендеряться в браузері, тож товари шукаємо в картах сайту і
 * відбираємо за початком слагу. Дані товару — у JSON стану сторінки ({@code <template id="reduxStore">}):
 * {@code price} — поточна ціна, {@code price_discount} — ціна без знижки; бренд, валюта та наявність — у мета
 * {@code product:*}. Належність до категорії перевіряється за шляхом категорій товару ({@code mpath}).
 */
@Slf4j
@Component
public class EldoradoAdapter extends AbstractShopAdapter {

    static final URI BASE = URI.create("https://eldorado.ua");

    /** {@code https://eldorado.ua/uk/<slug>/p<id>/} */
    private static final Pattern PRODUCT_URL = Pattern.compile("^https://eldorado\\.ua/uk/([a-z0-9\\-_]+)/p(\\d+)/$");
    /** Ідентифікатор категорії в її адресі: {@code /uk/washing_machines/c1039034/}. */
    private static final Pattern CATEGORY_ID = Pattern.compile("/c(\\d+)/?$");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** Очікувана категорія для кожної знайденої адреси (адаптер — один бін на всі обходи). */
    private final Map<URI, String> expectedCategory = new ConcurrentHashMap<>();

    public EldoradoAdapter(HttpFetcher fetcher, CatalogService catalog, CrawlerShopsProperties properties, Clock clock) {
        super(fetcher, catalog, properties, clock);
    }

    @Override
    public String shopCode() {
        return "ELDORADO";
    }

    @Override
    public List<URI> discoverOfferUrls(String categoryCode, int limit) {
        String categoryId = categoryId(categoryPath(categoryCode));
        List<String> prefixes = settings().productSlugPrefixes().getOrDefault(categoryCode, List.of());
        if (prefixes.isEmpty()) {
            throw new IllegalArgumentException("No product slug prefixes configured for ELDORADO " + categoryCode);
        }

        Set<URI> found = new LinkedHashSet<>();
        for (String sitemap : settings().productSitemaps()) {
            if (found.size() >= limit) {
                break;
            }
            for (URI uri : productLinks(fetchXmlDocument(URI.create(sitemap)), prefixes)) {
                if (found.size() >= limit) {
                    break;
                }
                if (found.add(uri)) {
                    expectedCategory.put(uri, categoryId);
                }
            }
        }
        log.info("ELDORADO {}: discovered {} offer URLs", categoryCode, found.size());
        return new ArrayList<>(found);
    }

    @Override
    public ScrapedOffer fetchOffer(URI url) {
        Document doc = fetchDocument(url);
        ScrapedOffer offer = parseOffer(doc, URI.create(doc.location()), clock.instant());
        String categoryId = expectedCategory.remove(url);
        if (categoryId != null) {
            requireCategory(doc, url, categoryId);
        }
        return offer;
    }

    // --------------------------------------------- розбір без мережі (тестується на фікстурах)

    static String categoryId(String categoryPath) {
        Matcher m = CATEGORY_ID.matcher(categoryPath);
        if (!m.find()) {
            throw new IllegalArgumentException("Eldorado category path must end with /c<id>/: " + categoryPath);
        }
        return m.group(1);
    }

    /** Сторінки товарів із карти сайту, слаг яких починається з одного з {@code slugPrefixes}. */
    static List<URI> productLinks(Document sitemap, List<String> slugPrefixes) {
        Set<URI> links = new LinkedHashSet<>();
        for (Element loc : sitemap.select("loc")) {
            Matcher m = PRODUCT_URL.matcher(loc.text().strip());
            if (m.matches() && slugPrefixes.stream().anyMatch(m.group(1)::startsWith)) {
                links.add(URI.create(m.group()));
            }
        }
        return new ArrayList<>(links);
    }

    /**
     * @throws OfferSkippedException якщо товар не належить до категорії з ідентифікатором {@code categoryId}
     */
    static void requireCategory(Document doc, URI pageUri, String categoryId) {
        List<String> path = Arrays.asList(product(doc, pageUri).path("mpath").asString("").split(","));
        if (!path.contains(categoryId)) {
            throw new OfferSkippedException(pageUri, "Product is not in category " + categoryId + " (mpath " + path + ")");
        }
    }

    ScrapedOffer parseOffer(Document doc, URI pageUri, Instant scrapedAt) {
        JsonNode product = product(doc, pageUri);

        String externalId = text(product, "ext_id");
        if (externalId == null) {
            Matcher m = PRODUCT_URL.matcher(pageUri.toString());
            if (!m.matches()) {
                throw new OfferParseException(pageUri, "Cannot determine Eldorado product id");
            }
            externalId = m.group(2);
        }
        String title = text(product, "title_ua");
        if (title == null) {
            title = text(product, "title");
        }
        if (title == null) {
            throw new OfferParseException(pageUri, "Product has no title");
        }

        // ціна 0.00 означає, що товар фактично не продається
        BigDecimal price = PriceText.parse(text(product, "price"));
        BigDecimal oldPrice = PriceText.parse(text(product, "price_discount"));
        String shortSpecs = text(product, "desc_small_ua");

        return new ScrapedOffer(
                externalId,
                pageUri,
                title,
                meta(doc, "product:brand"),
                text(product, "sale_barcode"),
                null,
                price,
                oldPrice,
                meta(doc, "product:price:currency"),
                SchemaOrg.availability(meta(doc, "product:availability")),
                shortSpecs == null ? Map.of() : Map.of("Короткі характеристики", shortSpecs),
                scrapedAt);
    }

    private static JsonNode product(Document doc, URI pageUri) {
        Element script = doc.selectFirst("template#reduxStore script");
        if (script == null) {
            throw new OfferParseException(pageUri, "No reduxStore state on page");
        }
        JsonNode product;
        try {
            product = JSON.readTree(script.data()).path("reduxAsyncConnect").path("product");
        } catch (RuntimeException e) {
            throw new OfferParseException(pageUri, "Malformed reduxStore JSON: " + e.getMessage());
        }
        if (!product.isObject()) {
            throw new OfferParseException(pageUri, "No product in reduxStore");
        }
        return product;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isString() && !value.isNumber()) {
            return null;
        }
        String text = value.asString().strip();
        return text.isEmpty() ? null : text;
    }

    private static String meta(Document doc, String property) {
        Element meta = doc.selectFirst("meta[property=" + property + "]");
        if (meta == null) {
            return null;
        }
        String content = meta.attr("content").strip();
        return content.isEmpty() ? null : content;
    }

}
