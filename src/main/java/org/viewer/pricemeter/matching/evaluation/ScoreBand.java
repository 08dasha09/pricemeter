package org.viewer.pricemeter.matching.evaluation;

/**
 * Діапазон оцінки схожості — шар вибірки для розмітки. Пари беруться з кожного діапазону порівну, інакше
 * вибірка складалась би майже лише з явно різних товарів (їх на порядки більше) і не містила б складних
 * випадків біля порогів.
 */
public enum ScoreBand {

    VERY_LOW(0.0, 0.30),
    LOW(0.30, 0.50),
    BELOW_REVIEW(0.50, 0.65),
    REVIEW_LOW(0.65, 0.80),
    REVIEW_HIGH(0.80, 0.90),
    AUTO_LOW(0.90, 0.95),
    AUTO_HIGH(0.95, 1.0);

    private final double from;
    private final double to;

    ScoreBand(double from, double to) {
        this.from = from;
        this.to = to;
    }

    /** Нижня межа включно. */
    public double from() {
        return from;
    }

    /** Верхня межа не включно (для останнього діапазону — включно). */
    public double to() {
        return to;
    }

    public static ScoreBand of(double score) {
        ScoreBand[] bands = values();
        for (int i = bands.length - 1; i >= 0; i--) {
            if (score >= bands[i].from) {
                return bands[i];
            }
        }
        return VERY_LOW;
    }

}
