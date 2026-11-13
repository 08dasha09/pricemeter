package org.viewer.pricemeter.matching.similarity;

/**
 * Оцінка схожості двох пропозицій.
 *
 * @param value  від 0 (різні товари) до 1 (той самий товар)
 * @param reason вирішальна ознака — для пояснення рішення й ручної модерації
 */
public record MatchScore(double value, Reason reason) {

    public enum Reason {
        /** Різні категорії або бренди — не порівнюються. */
        BLOCKED,
        /** Збігся штрихкод. */
        GTIN,
        /** Штрихкоди є в обох і різні. */
        GTIN_CONFLICT,
        /** Повний код моделі однаковий. */
        EXACT_CODE,
        /** Однаковий код без регіонального суфікса ({@code WW60A3120BE/LE} і {@code WW60A3120BE/UA}). */
        BASE_CODE,
        /** Один код — початок іншого ({@code WW80T4040} і {@code WW80T4040CE1UA}). */
        CODE_PREFIX,
        /** Ті самі цифри, інші літери — найчастіше колір чи комплектація ({@code GS520E15S} і {@code GS520E15W}). */
        SIMILAR_CODE,
        /** Коди відрізняються цифрами — різні моделі ({@code F2Y2HS5WE} і {@code F2Y2HS6WE}). */
        DIFFERENT_CODE,
        /** Код є не в обох — порівнюються лише назви. */
        TITLE_ONLY
    }

}
