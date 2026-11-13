package org.viewer.pricemeter.crawler.adapter;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.crawler.parser.Availability;
import org.viewer.pricemeter.pricehistory.service.PriceObservation;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ScrapedOfferTest {

    private static final Instant NOW = Instant.parse("2026-10-04T20:00:00Z");

    @Test
    void mapsToUpsertCommand() {
        OfferUpsertCommand cmd = offer("15999", "18999", Availability.IN_STOCK).toUpsertCommand("MOYO", "WASHING_MACHINES");

        assertThat(cmd.shopCode()).isEqualTo("MOYO");
        assertThat(cmd.categoryCode()).isEqualTo("WASHING_MACHINES");
        assertThat(cmd.externalId()).isEqualTo("123");
        assertThat(cmd.url()).isEqualTo("https://www.moyo.ua/ua/x/123.html");
        assertThat(cmd.mpn()).isEqualTo("EW6S404WU");
        assertThat(cmd.specs()).containsEntry("load", "8 кг");
        assertThat(cmd.seenAt()).isEqualTo(NOW);
    }

    @Test
    void inStockWithPriceIsAvailable() {
        PriceObservation obs = offer("15999", "18999", Availability.IN_STOCK).toPriceObservation();

        assertThat(obs.available()).isTrue();
        assertThat(obs.price()).isEqualByComparingTo("15999");
        assertThat(obs.oldPrice()).isEqualByComparingTo("18999");
    }

    @Test
    void unknownAvailabilityWithPriceIsAvailable() {
        assertThat(offer("15999", null, Availability.UNKNOWN).toPriceObservation().available()).isTrue();
    }

    @Test
    void outOfStockKeepsPriceButIsUnavailable() {
        PriceObservation obs = offer("15999", null, Availability.OUT_OF_STOCK).toPriceObservation();

        assertThat(obs.available()).isFalse();
        assertThat(obs.price()).isEqualByComparingTo("15999");
    }

    @Test
    void missingPriceIsUnavailable() {
        PriceObservation obs = offer(null, null, Availability.IN_STOCK).toPriceObservation();

        assertThat(obs.available()).isFalse();
        assertThat(obs.price()).isNull();
    }

    private static ScrapedOffer offer(String price, String oldPrice, Availability availability) {
        return new ScrapedOffer("123", URI.create("https://www.moyo.ua/ua/x/123.html"), "Electrolux EW6S404WU",
                "Electrolux", null, "EW6S404WU",
                price == null ? null : new BigDecimal(price), oldPrice == null ? null : new BigDecimal(oldPrice),
                "UAH", availability, Map.of("load", "8 кг"), NOW);
    }

}
