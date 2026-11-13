package org.viewer.pricemeter.matching.evaluation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThresholdSuggestionTest {

    @Test
    void gridCoversWholeRange() {
        List<Double> thresholds = MatchingEvaluator.thresholds();

        assertThat(thresholds).hasSize(20).startsWith(0.05, 0.1, 0.15).endsWith(0.95, 1.0).contains(0.65, 0.9);
    }

    @Test
    void autoThresholdIsLowestWherePrecisionHoldsAllTheWayUp() {
        List<MatchingEvaluation.ThresholdPoint> points = List.of(
                point(0.80, new Metrics(10, 1, 0, 50)),    // 0.91 — нижче цілі
                point(0.85, new Metrics(10, 0, 0, 51)),    // 1.0
                point(0.90, new Metrics(9, 0.1, 1, 51)),   // 0.989
                point(0.95, new Metrics(8, 0, 2, 51)),
                point(1.00, new Metrics(0, 0, 10, 51)));   // нічого не об'єднано — не рахується

        assertThat(MatchingEvaluator.suggestAuto(points)).isEqualTo(0.85);
    }

    @Test
    void autoThresholdStopsAtFirstPrecisionDropFromAbove() {
        List<MatchingEvaluation.ThresholdPoint> points = List.of(
                point(0.80, new Metrics(10, 0, 0, 50)),
                point(0.90, new Metrics(9, 1, 1, 50)),     // провал
                point(0.95, new Metrics(8, 0, 2, 51)));

        assertThat(MatchingEvaluator.suggestAuto(points)).isEqualTo(0.95);
    }

    @Test
    void reviewThresholdIsHighestKeepingRecall() {
        List<MatchingEvaluation.ThresholdPoint> points = List.of(
                point(0.30, new Metrics(100, 40, 0, 10)),
                point(0.50, new Metrics(99, 10, 1, 40)),   // 0.99
                point(0.65, new Metrics(95, 2, 5, 48)),    // 0.95 — нижче цілі
                point(0.90, new Metrics(99, 0, 1, 50)));   // після провалу не враховується

        assertThat(MatchingEvaluator.suggestReview(points)).isEqualTo(0.50);
        assertThat(MatchingEvaluator.suggestReview(List.of(point(0.3, new Metrics(0, 5, 0, 5))))).isNull();
    }

    @Test
    void metricsHandleEmptyCases() {
        assertThat(Metrics.EMPTY.precision()).isNull();
        assertThat(Metrics.EMPTY.recall()).isNull();
        assertThat(Metrics.EMPTY.f1()).isNull();
        Metrics m = Metrics.EMPTY.add(true, true, 3).add(true, false, 1).add(false, true, 1).add(false, false, 2);
        assertThat(m).isEqualTo(new Metrics(3, 1, 1, 2));
        assertThat(m.precision()).isEqualTo(0.75);
        assertThat(m.recall()).isEqualTo(0.75);
        assertThat(m.f1()).isEqualTo(0.75);
    }

    private static MatchingEvaluation.ThresholdPoint point(double threshold, Metrics weighted) {
        return new MatchingEvaluation.ThresholdPoint(threshold, new MatchingEvaluation.Estimate(weighted, weighted));
    }

}
