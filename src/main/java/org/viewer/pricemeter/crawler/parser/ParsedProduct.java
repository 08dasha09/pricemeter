package org.viewer.pricemeter.crawler.parser;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Дані товару, вилучені зі сторінки магазину. Будь-яке поле, крім {@code specs}
 * і {@code availability}, може бути {@code null}, якщо магазин його не публікує.
 *
 * @param name         назва товару
 * @param brand        бренд
 * @param gtin         штрихкод (як є, без нормалізації)
 * @param sku          артикул магазину
 * @param mpn          код моделі виробника
 * @param price        поточна ціна
 * @param oldPrice     заявлена «стара» (закреслена) ціна
 * @param currency     валюта, напр. UAH
 * @param availability наявність
 * @param url          канонічна адреса товару
 * @param specs        характеристики (назва → значення)
 */
public record ParsedProduct(
        String name,
        String brand,
        String gtin,
        String sku,
        String mpn,
        BigDecimal price,
        BigDecimal oldPrice,
        String currency,
        Availability availability,
        String url,
        Map<String, String> specs
) {

    public ParsedProduct {
        availability = availability == null ? Availability.UNKNOWN : availability;
        specs = specs == null ? Map.of() : Map.copyOf(specs);
    }

}
