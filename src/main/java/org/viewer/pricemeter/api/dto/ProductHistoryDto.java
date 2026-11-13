package org.viewer.pricemeter.api.dto;

import org.viewer.pricemeter.common.Period;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Денні ряди цін усіх пропозицій товару на спільній осі дат — лінія на кожен магазин.
 *
 * @param dates  спільна вісь: усі дні, за які є дані хоча б однієї пропозиції
 * @param series по ряду на пропозицію; {@code prices[i]} відповідає {@code dates[i]}
 */
public record ProductHistoryDto(Long productId, Period period, List<LocalDate> dates, List<Series> series) {

    /**
     * @param prices    ціна на кінець дня; {@code null} — немає даних або товару не було в наявності
     * @param confirmed чи підтверджено день спостереженням ({@code false} — магазин не обходили, показано останній
     *                  відомий стан); {@code null} — даних пропозиції за цей день немає
     */
    public record Series(Long offerId, String shopCode, String shopName, List<BigDecimal> prices,
                         List<Boolean> confirmed) {
    }

}
