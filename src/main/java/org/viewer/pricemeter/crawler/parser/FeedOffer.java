package org.viewer.pricemeter.crawler.parser;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Пропозиція з товарного фіду (YML або Google Merchant).
 *
 * @param id           ідентифікатор товару у фіді (артикул магазину)
 * @param name         назва товару
 * @param url          адреса сторінки товару
 * @param price        поточна ціна; може бути {@code null}
 * @param oldPrice     ціна без знижки, може бути {@code null}
 * @param currency     валюта, напр. UAH
 * @param availability наявність
 * @param categoryId   ідентифікатор категорії у фіді
 * @param categoryName назва категорії у фіді
 * @param brand        бренд (vendor)
 * @param mpn          код моделі (vendorCode / mpn)
 * @param gtin         штрихкод (barcode / gtin)
 * @param params       характеристики (назва → значення)
 */
public record FeedOffer(
        String id,
        String name,
        String url,
        BigDecimal price,
        BigDecimal oldPrice,
        String currency,
        Availability availability,
        String categoryId,
        String categoryName,
        String brand,
        String mpn,
        String gtin,
        Map<String, String> params
) {

    public FeedOffer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(url, "url");
        availability = availability == null ? Availability.UNKNOWN : availability;
        params = params == null ? Map.of() : Map.copyOf(params);
    }

}
