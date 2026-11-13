package org.viewer.pricemeter.catalog.service;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Дані пропозиції, отримані з магазину, для створення або оновлення {@code Offer}.
 *
 * @param shopCode     код магазину (MOYO, CITRUS, ...)
 * @param categoryCode код категорії, може бути {@code null}
 * @param externalId   артикул товару в магазині
 * @param url          адреса сторінки товару
 * @param title        назва товару в магазині
 * @param brand        бренд, як його вказав магазин, може бути {@code null}
 * @param gtin         штрихкод, може бути {@code null}
 * @param mpn          код моделі виробника, може бути {@code null}
 * @param specs        характеристики, може бути {@code null}
 * @param seenAt       момент отримання даних; {@code null} — поточний час
 */
public record OfferUpsertCommand(
        String shopCode,
        String categoryCode,
        String externalId,
        String url,
        String title,
        String brand,
        String gtin,
        String mpn,
        Map<String, String> specs,
        Instant seenAt
) {

    public OfferUpsertCommand {
        requireText(shopCode, "shopCode");
        requireText(externalId, "externalId");
        requireText(url, "url");
        requireText(title, "title");
    }

    /** Без коду моделі. */
    public OfferUpsertCommand(String shopCode, String categoryCode, String externalId, String url, String title,
                              String brand, String gtin, Map<String, String> specs, Instant seenAt) {
        this(shopCode, categoryCode, externalId, url, title, brand, gtin, null, specs, seenAt);
    }

    private static void requireText(String value, String name) {
        if (Objects.requireNonNull(value, name + " must not be null").isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

}
