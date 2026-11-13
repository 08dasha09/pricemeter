package org.viewer.pricemeter.matching.similarity;

import org.springframework.stereotype.Component;
import org.viewer.pricemeter.matching.normalize.ModelCode;

import java.util.Objects;

/**
 * Оцінює, наскільки дві пропозиції — той самий товар.
 *
 * <p>Головна ознака — код моделі; назва (косинус TF-IDF) лише уточнює оцінку в межах діапазону, який задає
 * збіг кодів. Пріоритет — точність: сусідні модифікації ({@code F2Y2HS5WE} / {@code F2Y2HS6WE}) мають
 * майже однакові назви, тому при різних кодах схожість назв оцінку не рятує.
 *
 * <table>
 *   <caption>Діапазони оцінки</caption>
 *   <tr><th>Ознака</th><th>Оцінка</th></tr>
 *   <tr><td>різні категорії / бренди</td><td>0</td></tr>
 *   <tr><td>однаковий GTIN</td><td>1</td></tr>
 *   <tr><td>однаковий повний код</td><td>0.95–1</td></tr>
 *   <tr><td>однаковий базовий код</td><td>0.88–0.95</td></tr>
 *   <tr><td>один код — початок іншого</td><td>0.70–0.85</td></tr>
 *   <tr><td>коду немає в одній з пропозицій</td><td>0–0.80</td></tr>
 *   <tr><td>ті самі цифри, інші літери</td><td>0–0.60</td></tr>
 *   <tr><td>коди відрізняються цифрами</td><td>0–0.30</td></tr>
 *   <tr><td>різні GTIN</td><td>не більше 0.20</td></tr>
 * </table>
 *
 * Пороги рішень (автоматично / на перевірку / новий товар) задає наступний крок конвеєра; діапазони
 * уточнимо на розміченій вибірці (модуль 9).
 */
@Component
public class SimilarityScorer {

    static final double EXACT_CODE_MIN = 0.95;
    static final double BASE_CODE_MIN = 0.88;
    static final double BASE_CODE_MAX = 0.95;
    static final double CODE_PREFIX_MIN = 0.70;
    static final double CODE_PREFIX_MAX = 0.85;
    static final double TITLE_ONLY_MAX = 0.80;
    static final double SIMILAR_CODE_MAX = 0.60;
    static final double DIFFERENT_CODE_MAX = 0.30;
    static final double GTIN_CONFLICT_MAX = 0.20;
    /** Коротший код, з якого може починатися інший: коротші ({@code S8}) збігаються випадково. */
    static final int MIN_PREFIX_LENGTH = 5;

    public MatchScore score(MatchCandidate a, MatchCandidate b) {
        if (conflicts(a.categoryCode(), b.categoryCode()) || conflicts(a.brand(), b.brand())) {
            return new MatchScore(0, MatchScore.Reason.BLOCKED);
        }
        if (a.gtin() != null && a.gtin().equals(b.gtin())) {
            return new MatchScore(1, MatchScore.Reason.GTIN);
        }

        MatchScore byCode = scoreByCode(a.modelCode(), b.modelCode(), a.titleVector().cosine(b.titleVector()));
        if (a.gtin() != null && b.gtin() != null) {
            return new MatchScore(Math.min(byCode.value(), GTIN_CONFLICT_MAX), MatchScore.Reason.GTIN_CONFLICT);
        }
        return byCode;
    }

    private static MatchScore scoreByCode(ModelCode a, ModelCode b, double title) {
        if (a == null || b == null) {
            return new MatchScore(TITLE_ONLY_MAX * title, MatchScore.Reason.TITLE_ONLY);
        }
        if (a.full().equals(b.full())) {
            return new MatchScore(between(EXACT_CODE_MIN, 1, title), MatchScore.Reason.EXACT_CODE);
        }
        if (a.base().equals(b.base())) {
            return new MatchScore(between(BASE_CODE_MIN, BASE_CODE_MAX, title), MatchScore.Reason.BASE_CODE);
        }
        if (isPrefix(a.base(), b.base()) || isPrefix(b.base(), a.base())) {
            return new MatchScore(between(CODE_PREFIX_MIN, CODE_PREFIX_MAX, title), MatchScore.Reason.CODE_PREFIX);
        }
        String digitsA = digits(a.base());
        if (!digitsA.isEmpty() && digitsA.equals(digits(b.base()))) {
            return new MatchScore(SIMILAR_CODE_MAX * JaroWinkler.similarity(a.full(), b.full()),
                    MatchScore.Reason.SIMILAR_CODE);
        }
        return new MatchScore(DIFFERENT_CODE_MAX * title, MatchScore.Reason.DIFFERENT_CODE);
    }

    /** Обидва значення відомі й різні; невідоме значення не заважає порівнянню. */
    private static boolean conflicts(String a, String b) {
        return a != null && b != null && !Objects.equals(a, b);
    }

    private static boolean isPrefix(String shorter, String longer) {
        return shorter.length() >= MIN_PREFIX_LENGTH && longer.startsWith(shorter);
    }

    private static String digits(String code) {
        return code.replaceAll("\\D", "");
    }

    private static double between(double min, double max, double weight) {
        return min + (max - min) * weight;
    }

}
