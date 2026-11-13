package org.viewer.pricemeter.crawler.adapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.crawler.parser.Availability;
import org.viewer.pricemeter.crawler.parser.JsonLdProductParser;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Розбір сторінок Цитрусу на збережених реальних сторінках (2026-10-04), без мережі.
 */
class CitrusAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-04T20:00:00Z");
    private static final String WASHERS = "/stiralnye-mashiny/";

    private final CitrusAdapter adapter = new CitrusAdapter(null, null, new CrawlerShopsProperties(Map.of()),
            Clock.systemUTC(), new JsonLdProductParser());

    @Test
    void extractsUniqueProductLinksOfCategory() {
        Document page = AdapterFixtures.document("citrus/category-washing-machines-page1.html",
                "https://citrus.ua/stiralnye-mashiny/");

        List<URI> links = CitrusAdapter.productLinks(page, WASHERS);

        assertThat(links).hasSizeGreaterThanOrEqualTo(20).doesNotHaveDuplicates();
        assertThat(links).allSatisfy(uri ->
                assertThat(uri.toString()).matches("https://citrus\\.ua/stiralnye-mashiny/[a-z0-9\\-]+-\\d+\\.html"));
        // кожен товар — один раз, навіть якщо на сторінці є і українська, і російська адреси
        assertThat(links.stream().map(u -> u.toString().replaceAll(".*-(\\d+)\\.html$", "$1")))
                .doesNotHaveDuplicates();
    }

    @Test
    void buildsPageUris() {
        assertThat(CitrusAdapter.pageUri(WASHERS, 1)).isEqualTo(URI.create("https://citrus.ua/stiralnye-mashiny/"));
        assertThat(CitrusAdapter.pageUri(WASHERS, 2))
                .isEqualTo(URI.create("https://citrus.ua/stiralnye-mashiny/page_2/"));
    }

    @Test
    void parsesDiscountedProduct() {
        URI uri = URI.create("https://citrus.ua/stiralnye-mashiny/stiralno-sushilnaya-mashina-lg-f2r5fg0w-780732.html");

        ScrapedOffer offer = adapter.parseOffer(
                AdapterFixtures.document("citrus/product-lg-f2r5fg0w-discount.html", uri.toString()), uri, NOW);

        assertThat(offer.externalId()).isEqualTo("780732");
        assertThat(offer.title()).isEqualTo("Прально-сушильна машина LG F2R5FG0W");
        assertThat(offer.price()).isEqualByComparingTo("31999");
        assertThat(offer.oldPrice()).isEqualByComparingTo("32999");
        assertThat(offer.currency()).isEqualTo("UAH");
        // Цитрус кладе в mpn свій числовий id — його не можна вважати кодом моделі
        assertThat(offer.mpn()).isNull();
    }

    @Test
    void parsesProductWithoutDiscount() {
        URI uri = URI.create("https://citrus.ua/holodilniki/dvukhkamernyy-kholodilnik-bosch-kgn36vl326-676087.html");

        ScrapedOffer offer = adapter.parseOffer(
                AdapterFixtures.document("citrus/product-bosch-kgn36vl326.html", uri.toString()), uri, NOW);

        assertThat(offer.externalId()).isEqualTo("676087");
        assertThat(offer.brand()).isEqualTo("BOSCH");
        assertThat(offer.price()).isEqualByComparingTo("37499");
        assertThat(offer.oldPrice()).isNull();
        assertThat(offer.availability()).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void failsOnPageWithoutProductMarkup() {
        URI uri = URI.create("https://citrus.ua/stiralnye-mashiny/some-item-1.html");
        Document empty = Jsoup.parse("<html><body>Товар не знайдено</body></html>", uri.toString());

        assertThatThrownBy(() -> adapter.parseOffer(empty, uri, NOW)).isInstanceOf(OfferParseException.class);
    }

}
