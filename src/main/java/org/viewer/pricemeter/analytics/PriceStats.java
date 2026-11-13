package org.viewer.pricemeter.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Статистики цін.
 */
public final class PriceStats {

    private PriceStats() {
    }

    /**
     * Медіана; для парної кількості — середнє двох середніх значень, округлене до копійок.
     *
     * @return медіана або {@code null}, якщо цін немає ({@code null}-значення пропускаються)
     */
    public static BigDecimal median(Collection<BigDecimal> prices) {
        List<BigDecimal> sorted = prices.stream().filter(Objects::nonNull).sorted().toList();
        return sorted.isEmpty() ? null : quantile(sorted, 0.5);
    }

    /**
     * Квантиль з лінійною інтерполяцією між сусідніми значеннями (як {@code PERCENTILE_CONT} у PostgreSQL),
     * округлений до копійок.
     *
     * @param sorted непорожній відсортований за зростанням список
     * @param q      частка від 0 до 1
     */
    static BigDecimal quantile(List<BigDecimal> sorted, double q) {
        double position = (sorted.size() - 1) * q;
        int lower = (int) Math.floor(position);
        BigDecimal value = sorted.get(lower);
        if (lower + 1 < sorted.size()) {
            BigDecimal fraction = BigDecimal.valueOf(position - lower);
            value = value.add(sorted.get(lower + 1).subtract(value).multiply(fraction));
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Перцентиль значення в розподілі за середнім рангом: {@code (менших + ½·рівних) / n · 100}, до десятих.
     * Рівні значення ділять ранг навпіл, тож незмінна весь період ціна має перцентиль 50, а не 0 чи 100.
     *
     * @param sorted непорожній відсортований за зростанням список
     */
    static double percentileRank(List<BigDecimal> sorted, BigDecimal value) {
        long below = sorted.stream().filter(p -> p.compareTo(value) < 0).count();
        long equal = sorted.stream().filter(p -> p.compareTo(value) == 0).count();
        double rank = (below + equal / 2.0) / sorted.size() * 100;
        return Math.round(rank * 10) / 10.0;
    }

    /**
     * Відхилення ціни від опорної, у відсотках, округлене до цілого: {@code 9 500} від {@code 10 000} → {@code -5}.
     *
     * @return відхилення або {@code null}, якщо одного зі значень немає
     */
    public static Integer deviationPercent(BigDecimal price, BigDecimal reference) {
        if (price == null || reference == null || reference.signum() <= 0) {
            return null;
        }
        return price.subtract(reference).movePointRight(2)
                .divide(reference, 0, RoundingMode.HALF_UP).intValueExact();
    }

}
