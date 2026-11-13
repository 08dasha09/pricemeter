package org.viewer.pricemeter.matching.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.catalog.service.MatchableOffer;
import org.viewer.pricemeter.matching.MatchingProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Оцінює зіставлення на розмічених парах: Precision / Recall / F1 поточних рішень і для сітки порогів,
 * підказує пороги. Пріоритет — точність: хибне об'єднання дає хибні вердикти й висновки про знижки, а
 * пропущений збіг лише зменшує кількість товарів з кількох магазинів.
 *
 * <p>Повнота рахується серед пар, які зіставлення порівнює (різні магазини, бренди без конфлікту): збіги,
 * відсічені блокуванням за брендом, вибірка не бачить.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingEvaluator {

    /** Точність, якої має досягати автоматичне об'єднання. */
    static final double TARGET_PRECISION = 0.98;
    /** Частка справжніх збігів, яку мають охоплювати автоматичне об'єднання й ручна перевірка разом. */
    static final double TARGET_RECALL = 0.98;
    static final double THRESHOLD_STEP = 0.05;

    private final PairScanner pairs;
    private final MatchLabelRepository labels;
    private final MatchingProperties properties;

    public MatchingEvaluation evaluate() {
        Map<OfferPair, MatchLabel> labelsByPair = new HashMap<>();
        labels.findAll().forEach(label -> labelsByPair.put(label.pair(), label));

        Map<ScoreBand, Long> population = new EnumMap<>(ScoreBand.class);
        for (ScoreBand band : ScoreBand.values()) {
            population.put(band, 0L);
        }
        List<Observation> observations = new ArrayList<>();
        pairs.scan(p -> {
            population.merge(ScoreBand.of(p.score().value()), 1L, Long::sum);
            MatchLabel label = labelsByPair.get(p.pair());
            if (label != null) {
                observations.add(new Observation(p, label.isSameProduct(), sameProduct(p.a(), p.b())));
            }
        });

        Map<ScoreBand, Integer> labeledPerBand = new EnumMap<>(ScoreBand.class);
        observations.forEach(o -> labeledPerBand.merge(o.band(), 1, Integer::sum));
        Map<ScoreBand, Double> weights = new EnumMap<>(ScoreBand.class);
        labeledPerBand.forEach((band, n) -> weights.put(band, (double) population.get(band) / n));

        Accumulator system = new Accumulator();
        List<Double> thresholds = thresholds();
        List<Accumulator> curve = thresholds.stream().map(t -> new Accumulator()).toList();
        List<MatchingEvaluation.PairOutcome> errors = new ArrayList<>();
        for (Observation o : observations) {
            double weight = weights.get(o.band());
            system.add(o.systemSame(), o.labeledSame(), weight);
            for (int i = 0; i < thresholds.size(); i++) {
                curve.get(i).add(o.pair().score().value() >= thresholds.get(i) - 1e-9, o.labeledSame(), weight);
            }
            if (o.systemSame() != o.labeledSame()) {
                PairScanner.ScoredPair p = o.pair();
                errors.add(new MatchingEvaluation.PairOutcome(p.categoryCode(),
                        LabelingTask.Side.of(p.a(), p.candidateA()), LabelingTask.Side.of(p.b(), p.candidateB()),
                        o.labeledSame(), o.systemSame(), p.score()));
            }
        }
        errors.sort(Comparator.comparingDouble((MatchingEvaluation.PairOutcome e) -> e.score().value()).reversed());

        List<MatchingEvaluation.ThresholdPoint> points = new ArrayList<>(thresholds.size());
        for (int i = 0; i < thresholds.size(); i++) {
            points.add(new MatchingEvaluation.ThresholdPoint(thresholds.get(i), curve.get(i).toEstimate()));
        }
        int same = (int) observations.stream().filter(Observation::labeledSame).count();
        return new MatchingEvaluation(observations.size(), same, labelsByPair.size() - observations.size(),
                system.toEstimate(), points, population, properties.autoThreshold(), properties.reviewThreshold(),
                TARGET_PRECISION, TARGET_RECALL, suggestAuto(points), suggestReview(points), errors);
    }

    /**
     * Найменший поріг, з якого (і для всіх вищих) зважена точність не нижча за ціль. Точність не монотонна,
     * тому йдемо згори й зупиняємось на першому провалі.
     */
    static Double suggestAuto(List<MatchingEvaluation.ThresholdPoint> points) {
        Double suggested = null;
        for (int i = points.size() - 1; i >= 0; i--) {
            Metrics weighted = points.get(i).estimate().weighted();
            if (weighted.tp() + weighted.fp() == 0) {
                continue;
            }
            if (weighted.precision() < TARGET_PRECISION) {
                break;
            }
            suggested = points.get(i).threshold();
        }
        return suggested;
    }

    /** Найбільший поріг, за якого зважена повнота не нижча за ціль: нижче нього збіги майже не трапляються. */
    static Double suggestReview(List<MatchingEvaluation.ThresholdPoint> points) {
        Double suggested = null;
        for (MatchingEvaluation.ThresholdPoint point : points) {
            Double recall = point.estimate().weighted().recall();
            if (recall == null || recall < TARGET_RECALL) {
                break;
            }
            suggested = point.threshold();
        }
        return suggested;
    }

    /** Пороги 0.05, 0.10 … 1.00. */
    static List<Double> thresholds() {
        List<Double> thresholds = new ArrayList<>();
        int steps = (int) Math.round(1 / THRESHOLD_STEP);
        for (int i = 1; i <= steps; i++) {
            thresholds.add(Math.round(i * THRESHOLD_STEP * 100) / 100.0);
        }
        return thresholds;
    }

    /** Рішення системи: обидві пропозиції належать одному товару. */
    private static boolean sameProduct(MatchableOffer a, MatchableOffer b) {
        return a.productId() != null && Objects.equals(a.productId(), b.productId());
    }

    private record Observation(PairScanner.ScoredPair pair, boolean labeledSame, boolean systemSame) {

        ScoreBand band() {
            return ScoreBand.of(pair.score().value());
        }

    }

    /** Накопичувач метрик: на вибірці (вага 1) і зважених. */
    private static final class Accumulator {
        private Metrics sample = Metrics.EMPTY;
        private Metrics weighted = Metrics.EMPTY;

        void add(boolean predicted, boolean actual, double weight) {
            sample = sample.add(predicted, actual, 1);
            weighted = weighted.add(predicted, actual, weight);
        }

        MatchingEvaluation.Estimate toEstimate() {
            return new MatchingEvaluation.Estimate(sample, weighted);
        }
    }

}
