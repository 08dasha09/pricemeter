package org.viewer.pricemeter.matching.evaluation;

import org.viewer.pricemeter.matching.similarity.MatchScore;

import java.util.List;
import java.util.Map;

/**
 * Якість зіставлення на розміченій вибірці.
 *
 * <p>Кожна метрика — у двох варіантах: «на вибірці» (кожна розмічена пара важить 1) і «зважена» — оцінка для
 * всіх пар, які порівнює зіставлення: пара діапазону оцінки важить {@code пар у діапазоні / розмічено в ньому}.
 * Вибірка стратифікована (діапазони порівну), тож без зважування складні пари біля порогів переоцінені.
 *
 * @param labeled          розмічених пар, які враховано
 * @param same             з них — той самий товар
 * @param skipped          мітки, пари яких зіставлення більше не порівнює (пропозиція зникла, бренди розійшлися)
 * @param system           поточні рішення: пропозиції пари належать одному товару
 * @param curve            оцінка «той самий товар, якщо оцінка ≥ порогу» для сітки порогів
 * @param population       скільки пар порівнює зіставлення в кожному діапазоні оцінки
 * @param autoThreshold    поточний поріг автоматичного об'єднання
 * @param reviewThreshold  поточний поріг ручної перевірки
 * @param suggestedAuto    найменший поріг, з якого зважена точність не нижча за {@code targetPrecision}
 * @param suggestedReview  найбільший поріг, до якого зважена повнота не нижча за {@code targetRecall}
 * @param errors           помилки поточних рішень: хибні об'єднання й пропущені збіги
 */
public record MatchingEvaluation(
        int labeled,
        int same,
        int skipped,
        Estimate system,
        List<ThresholdPoint> curve,
        Map<ScoreBand, Long> population,
        double autoThreshold,
        double reviewThreshold,
        double targetPrecision,
        double targetRecall,
        Double suggestedAuto,
        Double suggestedReview,
        List<PairOutcome> errors) {

    /** Метрики на вибірці й зважені. */
    public record Estimate(Metrics sample, Metrics weighted) {
    }

    public record ThresholdPoint(double threshold, Estimate estimate) {
    }

    /**
     * Розмічена пара, на якій поточне рішення помилилось.
     *
     * @param labeledSame рішення людини
     * @param systemSame  поточне рішення системи
     */
    public record PairOutcome(String categoryCode, LabelingTask.Side a, LabelingTask.Side b, boolean labeledSame,
                              boolean systemSame, MatchScore score) {

        public List<LabelingTask.Side> sides() {
            return List.of(a, b);
        }

    }

}
