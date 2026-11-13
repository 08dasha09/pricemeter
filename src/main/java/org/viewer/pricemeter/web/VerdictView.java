package org.viewer.pricemeter.web;

import org.viewer.pricemeter.analytics.Verdict;

/**
 * Вердикт для шаблону.
 *
 * @param tone        {@code good} / {@code neutral} / {@code warn} / {@code bad} — колір плашки
 * @param title       підсумок для користувача
 * @param shortTitle  коротко — для таблиці
 * @param explanation пояснення з цифрами
 * @param reduced     вердикт за однією ознакою — показуємо застереження
 */
public record VerdictView(String tone, String title, String shortTitle, String explanation, boolean reduced) {

    public static VerdictView of(Verdict verdict) {
        return new VerdictView(
                switch (verdict.level()) {
                    case BELOW_USUAL -> "good";
                    case USUAL_CHEAPER_ELSEWHERE -> "warn";
                    case ABOVE_USUAL -> "bad";
                    case USUAL, UNKNOWN -> "neutral";
                },
                switch (verdict.level()) {
                    case BELOW_USUAL -> "Ціна нижча за звичайну";
                    case USUAL -> "Звичайна ціна";
                    case USUAL_CHEAPER_ELSEWHERE -> "Звичайна для магазину, але в інших дешевше";
                    case ABOVE_USUAL -> "Ціна завищена";
                    case UNKNOWN -> "Оцінити поки не можна";
                },
                switch (verdict.level()) {
                    case BELOW_USUAL -> "нижча за звичайну";
                    case USUAL -> "звичайна";
                    case USUAL_CHEAPER_ELSEWHERE -> "в інших дешевше";
                    case ABOVE_USUAL -> "завищена";
                    case UNKNOWN -> "—";
                },
                verdict.explanation(),
                verdict.confidence() == Verdict.Confidence.REDUCED);
    }

}
