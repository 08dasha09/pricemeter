package org.viewer.pricemeter.analytics;

/**
 * Історична ознака: де поточна ціна в розподілі цін цього магазину за період.
 */
public enum HistoricalLevel {
    /** Нижче 25-го перцентиля — близько до історичного мінімуму. */
    LOW,
    /** Від 25-го до 75-го перцентиля включно. */
    NORMAL,
    /** Вище 75-го перцентиля — завищена відносно історичної норми. */
    HIGH,
    /** Історії менше {@link #MIN_DAYS} днів — ознака недоступна. */
    INSUFFICIENT_DATA,
    /** Зараз немає в наявності — оцінювати нічого. */
    NOT_AVAILABLE;

    /** Скільки днів у наявності потрібно для історичної ознаки (стартовий поріг). */
    public static final int MIN_DAYS = 14;
    public static final double LOW_PERCENTILE = 25;
    public static final double HIGH_PERCENTILE = 75;

    public static HistoricalLevel of(PriceStatistics statistics) {
        if (statistics.current() == null) {
            return statistics.days() == 0 ? INSUFFICIENT_DATA : NOT_AVAILABLE;
        }
        if (statistics.days() < MIN_DAYS) {
            return INSUFFICIENT_DATA;
        }
        double percentile = statistics.currentPercentile();
        if (percentile < LOW_PERCENTILE) {
            return LOW;
        }
        return percentile > HIGH_PERCENTILE ? HIGH : NORMAL;
    }

    /** Ознаку можна використовувати у вердикті. */
    public boolean known() {
        return this == LOW || this == NORMAL || this == HIGH;
    }
}
