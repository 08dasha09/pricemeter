package org.viewer.pricemeter.crawler.adapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.crawler.parser.Availability;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Розбір сторінок Eldorado на збережених реальних сторінках (2026-10-04), без мережі.
 */
class EldoradoAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-04T20:00:00Z");

    private final EldoradoAdapter adapter = new EldoradoAdapter(null, null, new CrawlerShopsProperties(Map.of()),
            Clock.systemUTC());

    @Test
    void selectsProductsFromSitemapBySlugPrefix() {
        Document sitemap = AdapterFixtures.xmlDocument("eldorado/sitemap-main-home-uk.xml", "https://eldorado.ua/");

        List<URI> washers = EldoradoAdapter.productLinks(sitemap, List.of("stiralnaya-mashina", "pralina-mashina"));

        assertThat(washers).hasSizeGreaterThanOrEqualTo(40).doesNotHaveDuplicates();
        assertThat(washers).allSatisfy(uri ->
                assertThat(uri.toString()).matches("https://eldorado\\.ua/uk/(stiralnaya|pralina)-mashina[a-z0-9\\-_]*/p\\d+/"));
    }

    @Test
    void extractsCategoryIdFromPath() {
        assertThat(EldoradoAdapter.categoryId("/uk/washing_machines/c1039034/")).isEqualTo("1039034");
        assertThat(EldoradoAdapter.categoryId("/uk/node/c1039037/")).isEqualTo("1039037");
        assertThatThrownBy(() -> EldoradoAdapter.categoryId("/uk/washing_machines/"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsesProductFromReduxState() {
        URI uri = URI.create("https://eldorado.ua/uk/pralina-mashina-whirpool-wrsb-7259-wb-ua/p71414277/");
        Document doc = AdapterFixtures.document("eldorado/product-whirlpool-wrsb7259-washer.html", uri.toString());

        ScrapedOffer offer = adapter.parseOffer(doc, uri, NOW);

        assertThat(offer.externalId()).isEqualTo("71414277");
        assertThat(offer.title()).isEqualTo("Пральна машина WHIRLPOOL WRSB 7259 WB UA");
        assertThat(offer.brand()).isEqualTo("WHIRLPOOL");
        assertThat(offer.price()).isEqualByComparingTo("19699");
        assertThat(offer.oldPrice()).isNull();
        assertThat(offer.currency()).isEqualTo("UAH");
        assertThat(offer.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(offer.specs()).containsKey("Короткі характеристики");
    }

    @Test
    void priceDiscountIsTheOldPrice() {
        URI uri = URI.create("https://eldorado.ua/uk/mikrohviliova-pich-vbudovana-electrolux-lms-2203-emx/p71000000/");
        Document doc = AdapterFixtures.document("eldorado/product-electrolux-microwave-discount.html", uri.toString());

        ScrapedOffer offer = adapter.parseOffer(doc, uri, NOW);

        assertThat(offer.title()).isEqualTo("Мікрохвильова піч вбудована ELECTROLUX LMS2203EMX");
        assertThat(offer.price()).isEqualByComparingTo("13299");
        assertThat(offer.oldPrice()).isEqualByComparingTo("14199");
    }

    @Test
    void zeroPriceMeansNotForSale() {
        URI uri = URI.create("https://eldorado.ua/uk/gorenje-gbf-50--ua/p71051219/");
        Document doc = AdapterFixtures.document("eldorado/product-gorenje-water-heater-no-price.html", uri.toString());

        ScrapedOffer offer = adapter.parseOffer(doc, uri, NOW);

        assertThat(offer.price()).isNull();
        assertThat(offer.toPriceObservation().available()).isFalse();
    }

    @Test
    void checksProductCategoryByMpath() {
        URI uri = URI.create("https://eldorado.ua/uk/pralina-mashina-whirpool-wrsb-7259-wb-ua/p71414277/");
        Document washer = AdapterFixtures.document("eldorado/product-whirlpool-wrsb7259-washer.html", uri.toString());

        // mpath пральної машини: 1088603,1039026,1039034
        assertThatCode(() -> EldoradoAdapter.requireCategory(washer, uri, "1039034")).doesNotThrowAnyException();
        assertThatThrownBy(() -> EldoradoAdapter.requireCategory(washer, uri, "1061560"))
                .isInstanceOf(OfferSkippedException.class);
    }

    @Test
    void failsWithoutReduxState() {
        URI uri = URI.create("https://eldorado.ua/uk/x/p1/");
        Document empty = Jsoup.parse("<html><body>Сторінку не знайдено</body></html>", uri.toString());

        assertThatThrownBy(() -> adapter.parseOffer(empty, uri, NOW)).isInstanceOf(OfferParseException.class);
    }

}
