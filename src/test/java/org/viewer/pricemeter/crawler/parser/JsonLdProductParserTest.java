package org.viewer.pricemeter.crawler.parser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLdProductParserTest {

    private static final URI PAGE = URI.create("https://shop.example.ua/catalog/item/");

    private final JsonLdProductParser parser = new JsonLdProductParser();

    @Test
    void parsesSimpleProduct() {
        ParsedProduct p = parser.parse(fixture("product-simple.html"), PAGE).orElseThrow();

        assertThat(p.name()).isEqualTo("Пральна машина Samsung WW80T4040CE1UA \"EcoBubble\"");
        assertThat(p.brand()).isEqualTo("Samsung");
        assertThat(p.sku()).isEqualTo("312345678");
        assertThat(p.mpn()).isEqualTo("WW80T4040CE1UA");
        assertThat(p.gtin()).isEqualTo("8806092000000");
        assertThat(p.price()).isEqualByComparingTo("15999");
        assertThat(p.oldPrice()).isNull();
        assertThat(p.currency()).isEqualTo("UAH");
        assertThat(p.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(p.url()).isEqualTo("https://shop.example.ua/samsung-ww80t4040ce1ua/p312345678/");
        assertThat(p.specs())
                .containsEntry("Максимальне завантаження", "8 кг")
                .containsEntry("Швидкість віджиму", "1400 об/хв");
    }

    @Test
    void findsProductInGraphAndPrefersInStockOffer() {
        ParsedProduct p = parser.parse(fixture("product-graph.html"), PAGE).orElseThrow();

        assertThat(p.name()).isEqualTo("Холодильник LG GA-B509CQSL");
        assertThat(p.brand()).isEqualTo("LG");
        assertThat(p.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(p.price()).isEqualByComparingTo("19499");
        assertThat(p.oldPrice()).isEqualByComparingTo("23999");
        assertThat(p.gtin()).isEqualTo("8801031000000");
        assertThat(p.url()).isNull();
    }

    @Test
    void parsesAggregateOfferOutOfStock() {
        ParsedProduct p = parser.parse(fixture("product-aggregate-out-of-stock.html"), PAGE).orElseThrow();

        assertThat(p.brand()).isEqualTo("Bosch");
        assertThat(p.price()).isEqualByComparingTo("18750.5");
        assertThat(p.availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void parsesRealCitrusPage() {
        URI uri = URI.create("https://citrus.ua/holodilniki/dvukhkamernyy-kholodilnik-bosch-kgn36vl326-676087.html");

        ParsedProduct p = parser.parse(Fixtures.read("citrus/product-bosch-kgn36vl326.html"), uri).orElseThrow();

        assertThat(p.name()).isEqualTo("Двокамерний холодильник BOSCH KGN36VL326");
        assertThat(p.brand()).isEqualTo("BOSCH");
        assertThat(p.sku()).isEqualTo("676087");
        assertThat(p.price()).isEqualByComparingTo("37499");
        assertThat(p.currency()).isEqualTo("UAH");
        assertThat(p.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(p.url()).isEqualTo(uri.toString());
    }

    @Test
    void returnsEmptyWithoutProductMarkup() {
        String html = """
                <html><head>
                <script type="application/ld+json">{"@type": "Organization", "name": "Shop"}</script>
                <script type="text/javascript">var product = {"@type": "Product"};</script>
                </head><body></body></html>
                """;

        assertThat(parser.parse(html, PAGE)).isEmpty();
    }

    @Test
    void returnsProductWithoutPriceWhenOffersMissing() {
        String html = """
                <script type="application/ld+json">{"@type": "Product", "name": "Mystery"}</script>
                """;

        ParsedProduct p = parser.parse(html, PAGE).orElseThrow();
        assertThat(p.name()).isEqualTo("Mystery");
        assertThat(p.price()).isNull();
        assertThat(p.availability()).isEqualTo(Availability.UNKNOWN);
        assertThat(p.specs()).isEmpty();
    }

    private static String fixture(String name) {
        try (InputStream in = JsonLdProductParserTest.class.getResourceAsStream("/fixtures/jsonld/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
