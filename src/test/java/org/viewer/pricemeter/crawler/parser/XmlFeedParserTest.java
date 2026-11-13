package org.viewer.pricemeter.crawler.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XmlFeedParserTest {

    private final XmlFeedParser parser = new XmlFeedParser();

    @Test
    void parsesYmlFeed() {
        List<FeedOffer> offers = new ArrayList<>();

        FeedStats stats = parseFixture("yml-feed.xml", offers);

        assertThat(stats).isEqualTo(new FeedStats(FeedFormat.YML, 3, 1));
        assertThat(offers).extracting(FeedOffer::id).containsExactly("312345678", "400001", "500002");

        FeedOffer samsung = offers.get(0);
        assertThat(samsung.name()).isEqualTo("Пральна машина Samsung WW80T4040CE1UA");
        assertThat(samsung.url()).isEqualTo("https://shop.example.ua/samsung-ww80t4040ce1ua/p312345678/");
        assertThat(samsung.price()).isEqualByComparingTo("15999");
        assertThat(samsung.oldPrice()).isEqualByComparingTo("18999");
        assertThat(samsung.currency()).isEqualTo("UAH");
        assertThat(samsung.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(samsung.categoryId()).isEqualTo("11");
        assertThat(samsung.categoryName()).isEqualTo("Пральні машини");
        assertThat(samsung.brand()).isEqualTo("Samsung");
        assertThat(samsung.mpn()).isEqualTo("WW80T4040CE1UA");
        assertThat(samsung.gtin()).isEqualTo("8806092000000");
        assertThat(samsung.params())
                .containsEntry("Максимальне завантаження", "8")
                .containsEntry("Швидкість віджиму", "1400 об/хв");
    }

    @Test
    void ymlHandlesUnavailableAndVendorModelOffers() {
        List<FeedOffer> offers = new ArrayList<>();
        parseFixture("yml-feed.xml", offers);

        FeedOffer lg = offers.get(1);
        assertThat(lg.availability()).isEqualTo(Availability.OUT_OF_STOCK);
        assertThat(lg.price()).isEqualByComparingTo("21999");
        assertThat(lg.oldPrice()).isNull();

        FeedOffer bosch = offers.get(2);
        assertThat(bosch.name()).isEqualTo("Пральна машина Bosch WAN28263UA");
        assertThat(bosch.mpn()).isEqualTo("WAN28263UA");
        assertThat(bosch.price()).isEqualByComparingTo("18750.50");
        assertThat(bosch.availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void parsesGoogleMerchantFeed() {
        List<FeedOffer> offers = new ArrayList<>();

        FeedStats stats = parseFixture("google-merchant-feed.xml", offers);

        assertThat(stats).isEqualTo(new FeedStats(FeedFormat.GOOGLE_MERCHANT, 2, 1));

        FeedOffer lg = offers.get(0);
        assertThat(lg.id()).isEqualTo("FX-1001");
        assertThat(lg.name()).isEqualTo("Холодильник LG GA-B509CQSL");
        assertThat(lg.price()).isEqualByComparingTo("19499");
        assertThat(lg.oldPrice()).isEqualByComparingTo("23999");
        assertThat(lg.currency()).isEqualTo("UAH");
        assertThat(lg.availability()).isEqualTo(Availability.IN_STOCK);
        assertThat(lg.brand()).isEqualTo("LG");
        assertThat(lg.gtin()).isEqualTo("8801031000000");
        assertThat(lg.mpn()).isEqualTo("GA-B509CQSL");
        assertThat(lg.categoryName()).isEqualTo("Побутова техніка > Холодильники");
        assertThat(lg.params()).containsEntry("Об'єм", "384 л");

        // елементи без префікса g: теж підтримуються
        FeedOffer bosch = offers.get(1);
        assertThat(bosch.price()).isEqualByComparingTo("20999");
        assertThat(bosch.oldPrice()).isNull();
        assertThat(bosch.availability()).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void rejectsUnknownFormat() {
        assertThatThrownBy(() -> parse("<catalog><product/></catalog>", new ArrayList<>()))
                .isInstanceOf(FeedFormatException.class)
                .hasMessageContaining("catalog");
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> parse("<yml_catalog><shop><offers><offer id=\"1\">", new ArrayList<>()))
                .isInstanceOf(FeedFormatException.class);
    }

    @Test
    void doesNotResolveExternalEntities() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE yml_catalog [<!ENTITY secret SYSTEM "file:///C:/Windows/win.ini">]>
                <yml_catalog><shop><offers>
                  <offer id="1"><url>https://x</url><name>&secret;</name><price>1</price></offer>
                </offers></shop></yml_catalog>
                """;
        List<FeedOffer> offers = new ArrayList<>();

        try {
            parse(xxe, offers);
        } catch (FeedFormatException expected) {
            // відмова розбирати документ — теж безпечний результат
        }

        assertThat(offers).noneMatch(o -> o.name().contains("fonts") || o.name().contains("extensions"));
    }

    private FeedStats parseFixture(String name, List<FeedOffer> sink) {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/feeds/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Fixture not found: " + name);
            }
            return parser.parse(in, sink::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FeedStats parse(String xml, List<FeedOffer> sink) {
        return parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), sink::add);
    }

}
