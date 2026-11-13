package org.viewer.pricemeter.crawler.adapter;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.crawler.http.HttpFetcher;
import org.viewer.pricemeter.crawler.parser.Availability;
import org.viewer.pricemeter.crawler.parser.MicrodataProductParser;
import org.viewer.pricemeter.crawler.parser.ParsedProduct;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Епіцентр (epicentrk.ua): товар у мікророзмітці Schema.org, стара ціна — у {@code priceSpecification}
 * з типом StrikethroughPrice (розбирає {@link MicrodataProductParser}).
 * <p>
 * robots.txt забороняє будь-які адреси з параметрами ({@code /*?*}), тож пагінація {@code ?PAGEN_1=N}
 * недоступна. Натомість обходимо першу сторінку категорії та сторінки фільтрів за брендом
 * ({@code /fs/brand-<бренд>/}) — це статичні дозволені адреси. Товари маркетплейсу ({@code mplc-…})
 * пропускаємо: збираємо лише власні пропозиції Епіцентру. Вітринні зразки ({@code vitryna-…}, «ВІТРИНА! …»)
 * теж: це уцінений виставковий екземпляр, а не той самий товар для порівняння цін.
 * <p>
 * Коли товару немає в наявності, сторінка лишається, але мікророзмітки Product на ній немає зовсім — лише
 * назва в {@code <h1 data-product-name>} і напис «Немає в наявності» (або «Товар недоступний у цьому регіоні» — є лише
 * в офлайн-магазинах, а краулер заходить без вибраного регіону). Таку сторінку розбираємо як пропозицію
 * без ціни й наявності.
 */
@Slf4j
@Component
public class EpicentrAdapter extends AbstractShopAdapter {

    static final URI BASE = URI.create("https://epicentrk.ua");

    private static final Pattern PRODUCT_URL = Pattern.compile("^https://epicentrk\\.ua/ua/shop/([a-z0-9\\-]+)\\.html$");
    private static final String MARKETPLACE_PREFIX = "mplc-";
    private static final String SHOWROOM_PREFIX = "vitryna-";
    /** Саме так магазин позначає зразок; «Вітрина холодильна …» — звичайний товар. */
    private static final Pattern SHOWROOM_TITLE = Pattern.compile("^\\s*ВІТРИНА!");
    /** Видимі написи замість ціни й кнопки «Купити», коли купити онлайн не можна. */
    static final List<String> NOT_ON_SALE_TEXTS = List.of("Немає в наявності", "Товар недоступний у цьому регіоні");

    private final MicrodataProductParser microdata;

    public EpicentrAdapter(HttpFetcher fetcher, CatalogService catalog, CrawlerShopsProperties properties, Clock clock,
                           MicrodataProductParser microdata) {
        super(fetcher, catalog, properties, clock);
        this.microdata = microdata;
    }

    @Override
    public String shopCode() {
        return "EPICENTR";
    }

    @Override
    public List<URI> discoverOfferUrls(String categoryCode, int limit) {
        String path = categoryPath(categoryCode);
        List<URI> brandPages = new ArrayList<>();
        List<URI> found = discoverFromCategoryPages(BASE.resolve(path), limit, EpicentrAdapter::productLinks,
                (doc, pageNo) -> {
                    if (pageNo == 1) {
                        brandPages.addAll(brandFilterPages(doc, path));
                    }
                    return pageNo - 1 < brandPages.size() ? Optional.of(brandPages.get(pageNo - 1)) : Optional.empty();
                },
                false);
        log.info("EPICENTR {}: discovered {} offer URLs ({} brand pages available)",
                categoryCode, found.size(), brandPages.size());
        return found;
    }

    @Override
    public ScrapedOffer fetchOffer(URI url) {
        Document doc = fetchDocument(url);
        return parseOffer(doc, URI.create(doc.location()), clock.instant());
    }

    // --------------------------------------------- розбір без мережі (тестується на фікстурах)

    /** Власні товари Епіцентру з карток каталогу (без маркетплейсу й вітринних зразків). */
    static List<URI> productLinks(Document page) {
        Set<URI> links = new LinkedHashSet<>();
        for (Element a : page.select("a[data-product-picture][href$=.html]")) {
            var m = PRODUCT_URL.matcher(a.absUrl("href"));
            if (m.matches() && !m.group(1).startsWith(MARKETPLACE_PREFIX) && !m.group(1).startsWith(SHOWROOM_PREFIX)) {
                links.add(URI.create(m.group()));
            }
        }
        return new ArrayList<>(links);
    }

    /** Сторінки фільтра «бренд» цієї категорії: {@code <категорія>fs/brand-lg/}, {@code fs/brend-ardesto/}. */
    static List<URI> brandFilterPages(Document categoryPage, String categoryPath) {
        Pattern brandPage = Pattern.compile("^" + Pattern.quote(BASE.resolve(categoryPath) + "fs/") + "(?:brand|brend)-[a-z0-9\\-]+/$");
        Set<URI> pages = new LinkedHashSet<>();
        for (Element a : categoryPage.select("a[href]")) {
            String href = a.absUrl("href");
            if (brandPage.matcher(href).matches()) {
                pages.add(URI.create(href));
            }
        }
        return new ArrayList<>(pages);
    }

    /**
     * @throws OfferSkippedException вітринний зразок — за адресою або назвою (на таких сторінках часто немає ні
     *                               мікророзмітки, ні напису «Немає в наявності»)
     */
    ScrapedOffer parseOffer(Document doc, URI pageUri, Instant scrapedAt) {
        if (externalId(pageUri).startsWith(SHOWROOM_PREFIX) || isShowroomTitle(doc.selectFirst("h1[data-product-name]"))) {
            throw new OfferSkippedException(pageUri, "Showroom sample");
        }
        Optional<ParsedProduct> parsed = microdata.parse(doc, pageUri);
        if (parsed.isEmpty()) {
            return parseOutOfStock(doc, pageUri, scrapedAt)
                    .orElseThrow(() -> new OfferParseException(pageUri, "No Product microdata"));
        }
        ParsedProduct product = parsed.get();
        if (product.name() == null) {
            throw new OfferParseException(pageUri, "Product has no name");
        }
        if (SHOWROOM_TITLE.matcher(product.name()).find()) {
            throw new OfferSkippedException(pageUri, "Showroom sample");
        }
        return new ScrapedOffer(
                externalId(pageUri),
                pageUri,
                product.name(),
                product.brand(),
                product.gtin(),
                product.mpn(),
                product.price(),
                product.oldPrice(),
                product.currency(),
                product.availability(),
                product.specs(),
                scrapedAt);
    }

    /**
     * Сторінка товару, якого немає в наявності: назва з {@code <h1 data-product-name>} і один з
     * {@link #NOT_ON_SALE_TEXTS} (власний текст елемента — рядки в скриптах сторінки не враховуються). Без напису сторінку вважаємо
     * зламаною, а не відсутнім товаром — щоб не сховати зміну верстки.
     */
    private static Optional<ScrapedOffer> parseOutOfStock(Document doc, URI pageUri, Instant scrapedAt) {
        Element title = doc.selectFirst("h1[data-product-name]");
        boolean outOfStock = NOT_ON_SALE_TEXTS.stream()
                .anyMatch(text -> !doc.select(":containsOwn(" + text + ")").isEmpty());
        if (title == null || title.text().isBlank() || !outOfStock) {
            return Optional.empty();
        }
        return Optional.of(new ScrapedOffer(externalId(pageUri), pageUri, title.text().strip(), null, null, null,
                null, null, null, Availability.OUT_OF_STOCK, Map.of(), scrapedAt));
    }

    private static boolean isShowroomTitle(Element title) {
        return title != null && SHOWROOM_TITLE.matcher(title.text()).find();
    }

    /**
     * Слаг з адреси. Код {@code itemprop="sku"} є не на всіх сторінках, а змішування двох джерел
     * дало б дублікати однієї пропозиції.
     */
    private static String externalId(URI pageUri) {
        var m = PRODUCT_URL.matcher(pageUri.toString());
        if (m.matches()) {
            return m.group(1);
        }
        throw new OfferParseException(pageUri, "Cannot determine Epicentr product id");
    }

}
