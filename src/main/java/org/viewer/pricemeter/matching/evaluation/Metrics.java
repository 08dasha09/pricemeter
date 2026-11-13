package org.viewer.pricemeter.matching.evaluation;

/**
 * Матриця помилок попарного зіставлення: «позитив» — пара, яку вважають тим самим товаром. Значення дробові,
 * бо у зваженій оцінці кожна розмічена пара представляє кілька пар генеральної сукупності.
 *
 * @param tp система об'єднала, і це той самий товар
 * @param fp система об'єднала різні товари — найшкідливіша помилка (хибний вердикт ринку)
 * @param fn система не об'єднала той самий товар
 * @param tn система не об'єднала різні товари
 */
public record Metrics(double tp, double fp, double fn, double tn) {

    public static final Metrics EMPTY = new Metrics(0, 0, 0, 0);

    public Metrics add(boolean predicted, boolean actual, double weight) {
        if (predicted) {
            return actual ? new Metrics(tp + weight, fp, fn, tn) : new Metrics(tp, fp + weight, fn, tn);
        }
        return actual ? new Metrics(tp, fp, fn + weight, tn) : new Metrics(tp, fp, fn, tn + weight);
    }

    /** Частка справжніх серед об'єднаних; {@code null}, якщо нічого не об'єднано. */
    public Double precision() {
        return tp + fp == 0 ? null : tp / (tp + fp);
    }

    /** Частка знайдених серед справжніх; {@code null}, якщо справжніх пар немає. */
    public Double recall() {
        return tp + fn == 0 ? null : tp / (tp + fn);
    }

    public Double f1() {
        Double p = precision();
        Double r = recall();
        if (p == null || r == null || p + r == 0) {
            return null;
        }
        return 2 * p * r / (p + r);
    }

}
