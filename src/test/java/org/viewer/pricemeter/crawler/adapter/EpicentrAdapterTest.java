package org.viewer.pricemeter.crawler.adapter;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.crawler.parser.Availability;
import org.viewer.pricemeter.crawler.parser.MicrodataProductParser;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Розбір сторінок Епіцентру на збережених реальних сторінках (2026-10-04), без мережі.
 */
class EpicentrAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-04T20:00:00Z");
    private static final String WASHERS = "/ua/shop/stiralnye-mashiny/";

    private final EpicentrAdapter adapter = new EpicentrAdapter(null, null, new CrawlerShopsProperties(Map.of()),
            Clock.systemUTC(), new MicrodataProductParser());

    private final Document category = AdapterFixtures.document("epicentr/category-washing-machines-page1.html",
            "https://epicentrk.ua/ua/shop/stiralnye-mashiny/");

    @Test
    void extractsOwnProductsAndSkipsMarketplace() {
        List<URI> links = EpicentrAdapter.productLinks(category);

        assertThat(links).hasSizeGreaterThanOrEqualTo(30).doesNotHaveDuplicates();
        assertThat(links).allSatisfy(uri -> {
            assertThat(uri.toString()).matches("https://epicentrk\\.ua/ua/shop/[a-z0-9\\-]+\\.html");
            assertThat(uri.toString()).doesNotContain("/mplc-");
        });
    }

    @Test
    void findsBrandFilterPagesWithoutQueryStrings() {
        List<URI> pages = EpicentrAdapter.brandFilterPages(category, WASHERS);

        assertThat(pages).isNotEmpty()
                .contains(URI.create("https://epicentrk.ua/ua/shop/stiralnye-mashiny/fs/brand-bosch/"))
                .contains(URI.create("https://epicentrk.ua/ua/shop/stiralnye-mashiny/fs/brend-ardesto/"));
        // лише один рівень фільтра і жодних параметрів (robots.txt забороняє /*?*)
        assertThat(pages).allSatisfy(uri -> {
            assertThat(uri.getQuery()).isNull();
            assertThat(uri.toString()).matches(".*/fs/(brand|brend)-[a-z0-9\\-]+/");
        });
    }

    @Test
    void parsesDiscountedProductWithStrikethroughPrice() {
        URI uri = URI.create("https://epicentrk.ua/ua/shop/pralna-mashyna-electrolux-ews6426wu.html");

        ScrapedOffer offer = adapter.parseOffer(
                AdapterFixtures.document("epicentr/product-electrolux-ews6426wu-discount.html", uri.toString()), uri, NOW);

        assertThat(offer.externalId()).isEqualTo("pralna-mashyna-electrolux-ews6426wu");
        assertThat(offer.title()).containsIgnoringCase("Electrolux").containsIgnoringCase("EWS6426WU");
        assertThat(offer.price()).isEqualByComparingTo("16299");
        assertThat(offer.oldPrice()).isEqualByComparingTo("18199");
        assertThat(offer.currency()).isEqualTo("UAH");
        assertThat(offer.availability()).isEqualTo(Availability.IN_STOCK);
    }

    @Test
    void parsesProductWithoutDiscount() {
        URI uri = URI.create("https://epicentrk.ua/ua/shop/pralna-mashyna-beko-bm1wfsu36233wb.html");

        ScrapedOffer offer = adapter.parseOffer(
                AdapterFixtures.document("epicentr/product-beko-bm1wfsu36233wb.html", uri.toString()), uri, NOW);

        assertThat(offer.externalId()).isEqualTo("pralna-mashyna-beko-bm1wfsu36233wb");
        assertThat(offer.brand()).isEqualTo("Beko");
        assertThat(offer.price()).isEqualByComparingTo("14699");
        assertThat(offer.oldPrice()).isNull();
    }

    @Test
    void parsesOutOfStockPageWithoutMicrodata() {
        URI uri = URI.create("https://epicentrk.ua/ua/shop/posudomyina-mashyna-whirlpool-w2f-hd624.html");

        ScrapedOffer offer = adapter.parseOffer(
                AdapterFixtures.document("epicentr/product-whirlpool-w2fhd624-out-of-stock.html", uri.toString()),
                uri, NOW);

        assertThat(offer.externalId()).isEqualTo("posudomyina-mashyna-whirlpool-w2f-hd624");
        assertThat(offer.title()).isEqualTo("Посудомийна машина Whirlpool W2F HD624");
        assertThat(offer.price()).isNull();
        assertThat(offer.availability()).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(offer.toPriceObservation().available()).isFalse();
    }

    @Test
    void parsesPageUnavailableInRegionAsOutOfStock() {
        // реальна сторінка 2026-10-18: без мікророзмітки, замість ціни — «Товар недоступний у цьому регіоні»
        URI uri = URI.create("https://epicentrk.ua/ua/shop/vbudovuvana-posudomyina-mashyna-whirlpool-w7iht58t.html");

        ScrapedOffer offer = adapter.parseOffer(
                AdapterFixtures.document("epicentr/product-whirlpool-w7iht58t-not-in-region.html", uri.toString()),
                uri, NOW);

        assertThat(offer.title()).isEqualTo("Вбудовувана посудомийна машина Whirlpool W7IHT58T");
        assertThat(offer.price()).isNull();
        assertThat(offer.availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void pageWithoutMicrodataAndStockNoteIsBroken() {
        URI uri = URI.create("https://epicentrk.ua/ua/shop/posudomyina-mashyna-whirlpool-w2f-hd624.html");
        // назва є, але ні розмітки, ні напису «Немає в наявності» — схоже на зміну верстки
        Document page = Jsoup.parse("<h1 data-product-name>Посудомийна машина Whirlpool W2F HD624</h1>"
                + "<script>var labels = {ended: 'Немає в наявності'};</script>", uri.toString());

        assertThatThrownBy(() -> adapter.parseOffer(page, uri, NOW))
                .isInstanceOf(OfferParseException.class)
                .hasMessageContaining("No Product microdata");
    }

    @Test
    void skipsShowroomSamplesInCategory() {
        Document page = Jsoup.parse("""
                <a data-product-picture href="/ua/shop/pralna-mashyna-samsung-ww80t3040bw-ua.html"></a>
                <a data-product-picture href="/ua/shop/vitryna-pralna-mashyna-samsung-ww80t3040bw-ua.html"></a>
                <a data-product-picture href="/ua/shop/mplc-pralna-mashyna-samsung-ww80t3040bw-ua.html"></a>
                """, "https://epicentrk.ua/ua/shop/stiralnye-mashiny/");

        assertThat(EpicentrAdapter.productLinks(page))
                .containsExactly(URI.create("https://epicentrk.ua/ua/shop/pralna-mashyna-samsung-ww80t3040bw-ua.html"));
    }

    @Test
    void skipsShowroomSamplePage() {
        // реальна сторінка 2026-10-18: «ВІТРИНА! …», без мікророзмітки й без видимого «Немає в наявності»
        URI uri = URI.create("https://epicentrk.ua/ua/shop/vitryna-pralna-mashyna-samsung-ww80t3040bw-ua.html");
        Document page = AdapterFixtures.document("epicentr/product-vitryna-samsung-ww80t3040bw-out-of-stock.html",
                uri.toString());

        assertThatThrownBy(() -> adapter.parseOffer(page, uri, NOW))
                .isInstanceOf(OfferSkippedException.class)
                .hasMessageContaining("Showroom sample");
    }

    @Test
    void skipsShowroomSampleByTitleWhenSlugIsOrdinary() {
        URI uri = URI.create("https://epicentrk.ua/ua/shop/kholodylnyk-samsung-rs80f65k2tua.html");
        Document withoutMicrodata = Jsoup.parse(
                "<h1 data-product-name>ВІТРИНА! Холодильник Samsung RS80F65K2TUA</h1>", uri.toString());
        Document withMicrodata = Jsoup.parse("""
                <div itemscope itemtype="https://schema.org/Product">
                  <h1 itemprop="name">ВІТРИНА! Холодильник Samsung RS80F65K2TUA</h1>
                  <div itemprop="offers" itemscope itemtype="https://schema.org/Offer">
                    <meta itemprop="price" content="83699"><meta itemprop="priceCurrency" content="UAH">
                    <link itemprop="availability" href="https://schema.org/InStock">
                  </div>
                </div>
                """, uri.toString());

        assertThatThrownBy(() -> adapter.parseOffer(withoutMicrodata, uri, NOW))
                .isInstanceOf(OfferSkippedException.class);
        assertThatThrownBy(() -> adapter.parseOffer(withMicrodata, uri, NOW))
                .isInstanceOf(OfferSkippedException.class);
    }

    @Test
    void displayFridgeIsNotShowroomSample() {
        URI uri = URI.create("https://epicentrk.ua/ua/shop/kholodylna-vitryna-snaige-cd290.html");
        Document page = Jsoup.parse("""
                <div itemscope itemtype="https://schema.org/Product">
                  <h1 itemprop="name">Вітрина холодильна Snaige CD290</h1>
                  <div itemprop="offers" itemscope itemtype="https://schema.org/Offer">
                    <meta itemprop="price" content="21999"><meta itemprop="priceCurrency" content="UAH">
                    <link itemprop="availability" href="https://schema.org/InStock">
                  </div>
                </div>
                """, uri.toString());

        assertThat(adapter.parseOffer(page, uri, NOW).price()).isEqualByComparingTo("21999");
    }

}
