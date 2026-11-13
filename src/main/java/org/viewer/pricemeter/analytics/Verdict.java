package org.viewer.pricemeter.analytics;

/**
 * Вердикт щодо поточної ціни пропозиції.
 *
 * @param level       підсумок для користувача
 * @param confidence  довіра до вердикту
 * @param historical  історична ознака (за період)
 * @param market      ринкова ознака
 * @param explanation пояснення українською, напр. «на 12% дешевше за медіану інших магазинів, 18-й перцентиль за квартал»
 */
public record Verdict(Level level, Confidence confidence, HistoricalAssessment historical, MarketAssessment market,
                      String explanation) {

    public enum Level {
        /** Ціна нижча за звичайну. */
        BELOW_USUAL,
        /** Звичайна ціна. */
        USUAL,
        /** Звичайна для магазину, але в інших магазинах помітно дешевше. */
        USUAL_CHEAPER_ELSEWHERE,
        /** Ціна завищена. */
        ABOVE_USUAL,
        /** Жодна ознака недоступна (мало історії й немає інших магазинів) або товару немає в наявності. */
        UNKNOWN
    }

    public enum Confidence {
        /** Обидві ознаки доступні. */
        HIGH,
        /** Доступна лише одна ознака. */
        REDUCED,
        /** Вердикту немає. */
        NONE
    }

}
