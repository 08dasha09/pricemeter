package org.viewer.pricemeter.api.dto;

import org.viewer.pricemeter.pricehistory.service.DailyPrice;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Точка графіка: стан ціни на кінець дня.
 *
 * @param price     ціна; {@code null}, якщо товару не було в наявності
 * @param oldPrice  заявлена «стара» ціна, якщо була
 * @param available чи був товар у наявності
 * @param confirmed {@code false} — магазин того дня не обходили, а ціна потім змінилась: показано останній відомий стан
 */
public record PricePointDto(LocalDate date, BigDecimal price, BigDecimal oldPrice, boolean available,
                            boolean confirmed) {

    public static PricePointDto of(DailyPrice day) {
        return day.available()
                ? new PricePointDto(day.date(), day.price(), day.oldPrice(), true, day.confirmed())
                : new PricePointDto(day.date(), null, null, false, day.confirmed());
    }

}
