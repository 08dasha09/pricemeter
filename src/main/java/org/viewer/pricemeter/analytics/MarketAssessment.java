package org.viewer.pricemeter.analytics;

import java.math.BigDecimal;

/**
 * Ринкова ознака: поточна ціна пропозиції проти медіани поточних цін того самого товару в інших магазинах.
 *
 * @param otherMedian      медіана цін інших магазинів (лише в наявності); {@code null} — порівнювати немає з чим
 * @param otherShops       скільки інших магазинів мають товар у наявності
 * @param deviationPercent відхилення від {@code otherMedian}, %
 * @param position         положення відносно ринку; {@code null}, якщо ознака недоступна
 */
public record MarketAssessment(BigDecimal otherMedian, int otherShops, Integer deviationPercent,
                               MarketPosition position) {

    public static final MarketAssessment UNAVAILABLE = new MarketAssessment(null, 0, null, null);

    public boolean known() {
        return position != null;
    }

}
