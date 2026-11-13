package org.viewer.pricemeter.matching.normalize;

/**
 * Канонічний код моделі.
 *
 * @param full   код повністю, лише латинські літери й цифри у верхньому регістрі: {@code WW80T4040CE1UA}
 * @param base   код без регіонального суфікса ({@code UA}, {@code /UK}, {@code /LE}…): {@code WW80T4040CE1};
 *               якщо суфікса немає — дорівнює {@link #full}
 * @param source звідки взято код
 */
public record ModelCode(String full, String base, Source source) {

    public enum Source {
        /** Код моделі, який магазин вказав окремо ({@code offer.mpn}). */
        MPN,
        /** Вилучено з назви пропозиції. */
        TITLE
    }

}
