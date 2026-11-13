package org.viewer.pricemeter.crawler.parser;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class MicrodataProductParserTest {

    private static final URI PAGE = URI.create("https://shop.example.ua/catalog/item/");

    private final MicrodataProductParser parser = new MicrodataProductParser();

    // ---------------------------------------------- реальні сторінки (збережені 2026-10-04)

    @Test
    void parsesRealMoyoPage() {
        URI uri = URI.create("https://www.moyo.ua/stiralnaya_mashina_electrolux_ew6s404wu/500548.html");

        ParsedProduct p = parser.parse(Fixtures.read("moyo/product-electrolux-ew6s404wu.html"), uri).orElseThrow();

        assertThat(p.name()).isEqualTo("Стиральная машина Electrolux EW6S404WU");
        assertThat(p.brand()).isEqualTo("Electrolux");
        assertThat(p.sku()).isEqualTo("EW6S404WU");
        assertThat(p.mpn()).isEqualTo("EW6S404WU");
        assertThat(p.price()).isEqualByComparingTo("15099");
        assertThat(p.currency()).isEqualTo("UAH");
        assertThat(p.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(p.url()).isEqualTo(uri.toString());
    }

    @Test
    void parsesRealEpicentrPage() {
        URI uri = URI.create("https://epicentrk.ua/ua/shop/pralna-mashyna-beko-bm1wfsu36233wb.html");

        ParsedProduct p = parser.parse(Fixtures.read("epicentr/product-beko-bm1wfsu36233wb.html"), uri).orElseThrow();

        assertThat(p.name()).isEqualTo("Пральна машина Beko BM1WFSU36233WB");
        assertThat(p.brand()).isEqualTo("Beko");
        assertThat(p.price()).isEqualByComparingTo("14699");
        assertThat(p.currency()).isEqualTo("UAH");
        assertThat(p.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(p.url()).isEqualTo(uri.toString());
    }

    // ------------------------------------------------------------- синтетичні випадки

    @Test
    void nestedScopesDoNotLeakIntoProduct() {
        String html = """
                <div itemscope itemtype="https://schema.org/Product">
                  <div itemprop="brand" itemscope itemtype="https://schema.org/Brand">
                    <meta itemprop="name" content="Bosch">
                  </div>
                  <h1 itemprop="name">  Посудомийна   машина Bosch SMS4HVI33E </h1>
                  <div itemprop="offers" itemscope itemtype="https://schema.org/Offer">
                    <meta itemprop="name" content="Назва пропозиції">
                    <span itemprop="price" content="18999.00">18 999 ₴</span>
                    <meta itemprop="priceCurrency" content="UAH">
                    <link itemprop="availability" href="https://schema.org/InStock">
                  </div>
                </div>
                """;

        ParsedProduct p = parser.parse(html, PAGE).orElseThrow();

        assertThat(p.name()).isEqualTo("Посудомийна машина Bosch SMS4HVI33E");
        assertThat(p.brand()).isEqualTo("Bosch");
        assertThat(p.price()).isEqualByComparingTo("18999");
    }

    @Test
    void readsOldPriceGtinSpecsAndRelativeUrl() {
        String html = """
                <div itemscope itemtype="http://schema.org/Product">
                  <span itemprop="name">LG GA-B509CQSL</span>
                  <span itemprop="brand">LG</span>
                  <meta itemprop="gtin13" content="8801031000000">
                  <div itemprop="additionalProperty" itemscope itemtype="https://schema.org/PropertyValue">
                    <span itemprop="name">Об'єм</span><span itemprop="value">384 л</span>
                  </div>
                  <div itemprop="offers" itemscope itemtype="https://schema.org/Offer">
                    <a itemprop="url" href="/lg-ga-b509cqsl/">LG</a>
                    <link itemprop="availability" href="http://schema.org/InStock">
                    <meta itemprop="priceCurrency" content="UAH">
                    <div itemprop="priceSpecification" itemscope itemtype="https://schema.org/UnitPriceSpecification">
                      <data itemprop="price" value="19499">19 499 ₴</data>
                    </div>
                    <div itemprop="priceSpecification" itemscope itemtype="https://schema.org/UnitPriceSpecification">
                      <link itemprop="priceType" href="https://schema.org/StrikethroughPrice">
                      <data itemprop="price" value="23999">23 999 ₴</data>
                    </div>
                  </div>
                </div>
                """;

        ParsedProduct p = parser.parse(html, PAGE).orElseThrow();

        assertThat(p.brand()).isEqualTo("LG");
        assertThat(p.gtin()).isEqualTo("8801031000000");
        assertThat(p.price()).isEqualByComparingTo("19499");
        assertThat(p.oldPrice()).isEqualByComparingTo("23999");
        assertThat(p.url()).isEqualTo("https://shop.example.ua/lg-ga-b509cqsl/");
        assertThat(p.specs()).containsEntry("Об'єм", "384 л");
    }

    @Test
    void prefersProductWithPricedOfferOverRelatedProducts() {
        String html = """
                <div itemscope itemtype="https://schema.org/Product">
                  <span itemprop="name">Схожий товар</span>
                </div>
                <div itemscope itemtype="https://schema.org/WebPage">
                  <div itemprop="mainEntity" itemscope itemtype="https://schema.org/Product">
                    <span itemprop="name">Головний товар</span>
                    <div itemprop="offers" itemscope itemtype="https://schema.org/AggregateOffer">
                      <meta itemprop="lowPrice" content="8499">
                      <link itemprop="availability" href="https://schema.org/OutOfStock">
                    </div>
                  </div>
                </div>
                """;

        ParsedProduct p = parser.parse(html, PAGE).orElseThrow();

        assertThat(p.name()).isEqualTo("Головний товар");
        assertThat(p.price()).isEqualByComparingTo("8499");
        assertThat(p.availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void returnsEmptyWithoutProductMicrodata() {
        String html = """
                <ol itemscope itemtype="https://schema.org/BreadcrumbList">
                  <li itemprop="itemListElement" itemscope itemtype="https://schema.org/ListItem">
                    <span itemprop="name">Побутова техніка</span>
                  </li>
                </ol>
                """;

        assertThat(parser.parse(html, PAGE)).isEmpty();
    }

}
