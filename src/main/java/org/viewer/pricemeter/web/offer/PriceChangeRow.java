package org.viewer.pricemeter.web.offer;

import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Рядок таблиці змін ціни: інтервал незмінного стану (час — київський).
 */
public record PriceChangeRow(LocalDateTime from, LocalDateTime to, BigDecimal price, BigDecimal oldPrice,
                             boolean available) {

    static PriceChangeRow of(PriceSnapshot snapshot) {
        return new PriceChangeRow(
                LocalDateTime.ofInstant(snapshot.getValidFrom(), TimeConfig.ZONE),
                LocalDateTime.ofInstant(snapshot.getValidTo(), TimeConfig.ZONE),
                snapshot.getPrice(), snapshot.getOldPrice(), snapshot.isAvailable());
    }

}
