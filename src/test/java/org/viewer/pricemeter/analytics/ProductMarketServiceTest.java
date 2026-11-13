package org.viewer.pricemeter.analytics;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Shop;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMarketServiceTest {

    private static final Instant SEEN = Instant.parse("2026-10-18T09:00:00Z");

    @Test
    void inactiveOfferIsKeptButLeftOutOfMarket() {
        Offer moyo = offer(1L, "MOYO", "Moyo");
        Offer citrus = offer(2L, "CITRUS", "Цитрус");
        Offer epicentr = offer(3L, "EPICENTR", "Епіцентр");
        // зникла з сайту, остання ціна — найнижча, але купити за неї вже не можна
        epicentr.setActive(false);
        Map<Long, PriceSnapshot> prices = Map.of(
                1L, snapshot(1L, "20000"),
                2L, snapshot(2L, "22000"),
                3L, snapshot(3L, "15000"));

        ProductMarket market = ProductMarketService.build(List.of(moyo, citrus, epicentr), prices);

        assertThat(market.offers()).hasSize(3);
        assertThat(market.median()).isEqualByComparingTo("21000");
        assertThat(market.minPrice()).isEqualByComparingTo("20000");
        MarketOffer gone = market.offers().getLast();
        assertThat(gone.id()).isEqualTo(3L);
        assertThat(gone.active()).isFalse();
        assertThat(gone.available()).isFalse();
        assertThat(gone.purchasable()).isFalse();
        assertThat(gone.deviationPercent()).isNull();
        assertThat(gone.cheapest()).isFalse();
        assertThat(market.offers().getFirst().cheapest()).isTrue();
    }

    private static Offer offer(Long id, String shopCode, String shopName) {
        Offer offer = new Offer(new Shop(shopCode, shopName, "https://" + shopCode.toLowerCase()), "ext-" + id,
                "https://shop/" + id, "LG F2Y2HS5WE");
        offer.setId(id);
        offer.setLastSeenAt(SEEN);
        return offer;
    }

    private static PriceSnapshot snapshot(Long offerId, String price) {
        return new PriceSnapshot(offerId, new BigDecimal(price), null, true, SEEN);
    }

}
