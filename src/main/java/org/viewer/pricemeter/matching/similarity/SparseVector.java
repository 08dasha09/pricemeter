package org.viewer.pricemeter.matching.similarity;

import java.util.Map;

/**
 * Розріджений вектор ознак, нормований до одиничної довжини (або порожній).
 */
public record SparseVector(Map<String, Double> weights) {

    public static final SparseVector EMPTY = new SparseVector(Map.of());

    public SparseVector {
        weights = Map.copyOf(weights);
    }

    /** Косинусна схожість; для нормованих векторів — скалярний добуток. */
    public double cosine(SparseVector other) {
        Map<String, Double> small = weights.size() <= other.weights.size() ? weights : other.weights;
        Map<String, Double> large = small == weights ? other.weights : weights;
        double dot = 0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double w = large.get(e.getKey());
            if (w != null) {
                dot += e.getValue() * w;
            }
        }
        // захист від похибки округлення: 1.0000000002 → 1.0
        return Math.min(1.0, dot);
    }

}
