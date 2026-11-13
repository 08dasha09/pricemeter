package org.viewer.pricemeter.api.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Товар у списку.
 *
 * @param shopCodes        магазини з пропозиціями товару
 * @param minPrice         найнижча ціна серед наявних; {@code null} — ніде немає
 * @param purchasableShops у скількох магазинах товар можна купити зараз
 */
public record ProductSummaryDto(
        Long id,
        String title,
        String categoryCode,
        String brand,
        String modelCode,
        List<String> shopCodes,
        BigDecimal minPrice,
        int purchasableShops) {
}
