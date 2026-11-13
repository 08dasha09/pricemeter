package org.viewer.pricemeter.crawler.adapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.crawler.parser.Availability;
import org.viewer.pricemeter.crawler.parser.MicrodataProductParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Розбір сторінок Moyo на збережених реальних сторінках (2026-10-04), без мережі.
 */
class MoyoAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-04T20:00:00Z");

    private final MoyoAdapter adapter = new MoyoAdapter(null, null, new CrawlerShopsProperties(Map.of()),
            Clock.systemUTC(), new MicrodataProductParser());

    @Test
    void extractsProductLinksFromCategoryCards() {
        Document page = fixture("category-washing-machines-ua-page1.html",
                "https://www.moyo.ua/ua/bt/kbt/stiralnie-mashiny/");

        List<URI> links = MoyoAdapter.productLinks(page);

        assertThat(links).hasSize(24).doesNotHaveDuplicates();
        assertThat(links).allSatisfy(uri ->
                assertThat(uri.toString()).matches("https://www\\.moyo\\.ua/ua/[^/]+/\\d+\\.html"));
    }

    @Test
    void findsNextPage() {
        Document page = fixture("category-washing-machines-ua-page1.html",
                "https://www.moyo.ua/ua/bt/kbt/stiralnie-mashiny/");

        assertThat(MoyoAdapter.nextPage(page))
                .contains(URI.create("https://www.moyo.ua/ua/bt/kbt/stiralnie-mashiny/?page=2"));
    }

    @Test
    void parsesDiscountedProductWithOldPrice() {
        URI uri = URI.create("https://www.moyo.ua/ua/pralno-sushylna_mashyna_samsung_wd11db7b85gwua/773883.html");

        ScrapedOffer offer = adapter.parseOffer(fixture("product-samsung-wd11db7b85gwua-ua.html", uri.toString()), uri, NOW);

        assertThat(offer.externalId()).isEqualTo("773883");
        assertThat(offer.url()).isEqualTo(uri);
        assertThat(offer.title()).containsIgnoringCase("Samsung").containsIgnoringCase("WD11DB7B85GWUA");
        assertThat(offer.brand()).isEqualToIgnoringCase("Samsung");
        assertThat(offer.price()).isEqualByComparingTo("42999");
        assertThat(offer.oldPrice()).isEqualByComparingTo("46399");
        assertThat(offer.currency()).isEqualTo("UAH");
        assertThat(offer.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(offer.scrapedAt()).isEqualTo(NOW);
    }

    @Test
    void takesExternalIdFromMainProductBlock() {
        URI uri = URI.create("https://www.moyo.ua/stiralnaya_mashina_electrolux_ew6s404wu/500548.html");

        ScrapedOffer offer = adapter.parseOffer(fixture("product-electrolux-ew6s404wu.html", uri.toString()), uri, NOW);

        assertThat(offer.externalId()).isEqualTo("500548");
        assertThat(offer.mpn()).isEqualTo("EW6S404WU");
        assertThat(offer.price()).isEqualByComparingTo("15099");
        assertThat(offer.oldPrice()).isEqualByComparingTo("16399");
    }

    @Test
    void failsOnPageWithoutProduct() {
        URI uri = URI.create("https://www.moyo.ua/ua/bt/kbt/stiralnie-mashiny/");
        Document category = fixture("category-washing-machines-ua-page1.html", uri.toString());

        assertThatThrownBy(() -> adapter.parseOffer(category, uri, NOW))
                .isInstanceOf(OfferParseException.class);
    }

    private static Document fixture(String name, String baseUri) {
        try (InputStream in = MoyoAdapterTest.class.getResourceAsStream("/fixtures/moyo/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return Jsoup.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), baseUri);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
