package org.viewer.pricemeter.crawler.adapter;

import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.crawler.parser.Availability;
import org.viewer.pricemeter.pricehistory.service.PriceObservation;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Пропозиція, зібрана адаптером зі сторінки товару магазину.
 *
 * @param externalId   артикул товару в магазині
 * @param url          адреса сторінки товару
 * @param title        назва товару
 * @param brand        бренд, може бути {@code null}
 * @param gtin         штрихкод, може бути {@code null}
 * @param mpn          код моделі виробника, може бути {@code null}
 * @param price        поточна ціна, може бути {@code null}
 * @param oldPrice     заявлена «стара» ціна, може бути {@code null}
 * @param currency     валюта, може бути {@code null}
 * @param availability наявність
 * @param specs        характеристики
 * @param scrapedAt    момент отримання сторінки
 */
public record ScrapedOffer(
        String externalId,
        URI url,
        String title,
        String brand,
        String gtin,
        String mpn,
        BigDecimal price,
        BigDecimal oldPrice,
        String currency,
        Availability availability,
        Map<String, String> specs,
        Instant scrapedAt
) {

    public ScrapedOffer {
        Objects.requireNonNull(externalId, "externalId");
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(scrapedAt, "scrapedAt");
        availability = availability == null ? Availability.UNKNOWN : availability;
        specs = specs == null ? Map.of() : Map.copyOf(specs);
    }

    public OfferUpsertCommand toUpsertCommand(String shopCode, String categoryCode) {
        return new OfferUpsertCommand(shopCode, categoryCode, externalId, url.toString(), title,
                brand, gtin, mpn, specs, scrapedAt);
    }

    /**
     * Спостереження ціни. Товар вважається доступним, якщо є ціна і магазин не позначив його
     * відсутнім (невідома наявність при наявній ціні — доступний).
     */
    public PriceObservation toPriceObservation() {
        boolean available = price != null && availability != Availability.OUT_OF_STOCK;
        return new PriceObservation(price, oldPrice, available, scrapedAt);
    }

}
