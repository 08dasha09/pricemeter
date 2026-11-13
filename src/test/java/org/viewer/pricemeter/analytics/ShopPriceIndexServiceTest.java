package org.viewer.pricemeter.analytics;

import org.junit.jupiter.api.Test;
import org.viewer.pricemeter.analytics.ShopPriceIndexService.ShopPriceIndex;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShopPriceIndexServiceTest {

    @Test
    void comparesEachShopWithMedianOfOthers() {
        // товар 1: Moyo 9 000, Цитрус 10 000, Епіцентр 11 000; товар 2: Moyo 20 000, Цитрус 19 000
        List<ProductMarket> markets = List.of(
                market(offer(1, "MOYO", "9000", true), offer(2, "CITRUS", "10000", true),
                        offer(3, "EPICENTR", "11000", true)),
                market(offer(4, "MOYO", "20000", true), offer(5, "CITRUS", "19000", true)));

        List<ShopPriceIndex> index = ShopPriceIndexService.compare(markets);

        // Moyo: −14% (9 000 проти 10 500) і +5% → медіана −4.5 ≈ −4; найдешевший на 1 з 2
        assertThat(index).extracting(ShopPriceIndex::shopCode).containsExactly("MOYO", "CITRUS", "EPICENTR");
        ShopPriceIndex moyo = index.getFirst();
        assertThat(moyo.products()).isEqualTo(2);
        assertThat(moyo.medianDeviationPercent()).isEqualTo(-4);
        assertThat(moyo.cheapest()).isEqualTo(1);
        assertThat(moyo.cheapestPercent()).isEqualTo(50);
        // Епіцентр: 11 000 проти медіани 9 500 → +16%, лише один товар
        assertThat(index.getLast().medianDeviationPercent()).isEqualTo(16);
        assertThat(index.getLast().products()).isEqualTo(1);
    }

    @Test
    void usesCheapestOfferOfShopAndSkipsProductsWithOneShopInStock() {
        List<ProductMarket> markets = List.of(
                // дві пропозиції Moyo — береться дешевша; Цитрусу немає в наявності — порівнювати нема з чим
                market(offer(1, "MOYO", "9000", true), offer(2, "MOYO", "12000", true),
                        offer(3, "CITRUS", "8000", false)),
                market(offer(4, "MOYO", "10000", true), offer(5, "MOYO", "15000", true),
                        offer(6, "CITRUS", "10000", true)));

        List<ShopPriceIndex> index = ShopPriceIndexService.compare(markets);

        assertThat(index).allSatisfy(shop -> {
            assertThat(shop.products()).isEqualTo(1);
            assertThat(shop.medianDeviationPercent()).isZero();
            // нарівні — ніхто не найдешевший сам
            assertThat(shop.cheapest()).isZero();
            assertThat(shop.tiedCheapest()).isEqualTo(1);
        });
    }

    private static ProductMarket market(MarketOffer... offers) {
        return new ProductMarket(List.of(offers), null, null, offers.length, List.of());
    }

    private static MarketOffer offer(long id, String shop, String price, boolean available) {
        return new MarketOffer(id, shop, shop, "LG F2Y2HS5WE", "https://shop/" + id, new BigDecimal(price), null,
                available, true, LocalDateTime.of(2026, 10, 19, 4, 0), null, false);
    }

}
