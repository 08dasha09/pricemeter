package org.viewer.pricemeter.analytics;

import org.viewer.pricemeter.pricehistory.service.DailyPrice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Останнє зниження ціни за період: день, коли ціна в наявності стала нижчою за попередню відому (дні без наявності
 * пропускаються — повернення в продаж дешевше теж зниження).
 *
 * @param date          день зниження (стан на кінець дня, за київським часом)
 * @param previousPrice ціна до зниження
 * @param price         ціна після зниження
 * @param depthPercent  глибина зниження, %, округлена до цілого
 * @param daysAgo       скільки днів минуло від зниження до сьогодні
 */
public record PriceDrop(LocalDate date, BigDecimal previousPrice, BigDecimal price, int depthPercent, long daysAgo) {

    /** Менші зміни (копійки, округлення) — шум: вони не мають затуляти справжнє зниження перед ними. */
    static final BigDecimal MIN_DEPTH = new BigDecimal("0.01");

    /**
     * @param series денний ряд від старого до нового ({@code PriceHistoryService.getDailySeries})
     */
    public static Optional<PriceDrop> lastIn(List<DailyPrice> series, LocalDate today) {
        PriceDrop last = null;
        BigDecimal previous = null;
        for (DailyPrice day : series) {
            if (!day.available() || day.price() == null) {
                continue;
            }
            if (previous != null && isDrop(previous, day.price())) {
                last = new PriceDrop(day.date(), previous, day.price(), depthPercent(previous, day.price()),
                        ChronoUnit.DAYS.between(day.date(), today));
            }
            previous = day.price();
        }
        return Optional.ofNullable(last);
    }

    private static boolean isDrop(BigDecimal before, BigDecimal after) {
        return before.subtract(after).compareTo(before.multiply(MIN_DEPTH)) >= 0;
    }

    private static int depthPercent(BigDecimal before, BigDecimal after) {
        return before.subtract(after).movePointRight(2).divide(before, 0, RoundingMode.HALF_UP).intValue();
    }

}
