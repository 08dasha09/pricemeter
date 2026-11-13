package org.viewer.pricemeter.matching.evaluation;

import java.util.Map;

/**
 * Скільки пар розмічено — усього й за діапазонами оцінки на момент розмітки.
 */
public record LabelingProgress(int total, int same, Map<ScoreBand, Band> bands) {

    public int different() {
        return total - same;
    }

    public record Band(int labeled, int same) {

        public int different() {
            return labeled - same;
        }

    }

}
