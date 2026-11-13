package org.viewer.pricemeter.pricehistory.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Стан ціни пропозиції на кінець календарного дня.
 *
 * @param date      день (за київським часом)
 * @param price     ціна; {@code null}, якщо товару не було в наявності без ціни
 * @param oldPrice  заявлена «стара» ціна, може бути {@code null}
 * @param available чи був товар у наявності
 * @param confirmed чи підтверджено цей стан спостереженням того дня або пізніше; {@code false} — магазин між
 *                  спостереженнями не обходили і ціна змінилась: показано останній відомий стан, справжній невідомий
 */
public record DailyPrice(LocalDate date, BigDecimal price, BigDecimal oldPrice, boolean available, boolean confirmed) {

    /** Підтверджений спостереженням день. */
    public DailyPrice(LocalDate date, BigDecimal price, BigDecimal oldPrice, boolean available) {
        this(date, price, oldPrice, available, true);
    }

}
