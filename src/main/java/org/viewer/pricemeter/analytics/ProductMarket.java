package org.viewer.pricemeter.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Поточні пропозиції товару в усіх магазинах.
 *
 * @param offers           у наявності — від найдешевшої, без наявності — в кінці
 * @param minPrice         найнижча ціна серед наявних; {@code null} — ніде немає
 * @param median           медіана наявних цін, якщо їх ≥ 2; інакше {@code null}
 * @param purchasableCount скільки пропозицій можна купити зараз
 * @param shops            магазини з пропозиціями товару (за назвою)
 */
public record ProductMarket(
        List<MarketOffer> offers,
        BigDecimal minPrice,
        BigDecimal median,
        int purchasableCount,
        List<Shop> shops) {

    public static final ProductMarket EMPTY = new ProductMarket(List.of(), null, null, 0, List.of());

    /** Скільки різних магазинів мають товар у наявності. */
    public int purchasableShops() {
        return (int) offers.stream().filter(MarketOffer::purchasable).map(MarketOffer::shopCode).distinct().count();
    }

    public record Shop(String code, String name) {
    }

}
