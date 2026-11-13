package org.viewer.pricemeter.analytics;

/**
 * Положення ціни відносно ринкової медіани.
 */
public enum MarketPosition {
    /** Дешевше за медіану більш ніж на {@link #TOLERANCE_PERCENT}%. */
    BELOW_MARKET,
    /** У межах ±{@link #TOLERANCE_PERCENT}% від медіани. */
    AT_MARKET,
    /** Дорожче за медіану більш ніж на {@link #TOLERANCE_PERCENT}%. */
    ABOVE_MARKET;

    /** Стартовий поріг «рівня ринку» з опису вердикту; уточнюється на накопичених даних. */
    public static final int TOLERANCE_PERCENT = 5;

    /**
     * @param deviationPercent відхилення від медіани, %
     * @return положення або {@code null}, якщо відхилення невідоме
     */
    public static MarketPosition of(Integer deviationPercent) {
        if (deviationPercent == null) {
            return null;
        }
        if (deviationPercent < -TOLERANCE_PERCENT) {
            return BELOW_MARKET;
        }
        return deviationPercent > TOLERANCE_PERCENT ? ABOVE_MARKET : AT_MARKET;
    }
}
