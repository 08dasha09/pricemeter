package org.viewer.pricemeter.matching.similarity;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * TF-IDF символьних 3-грам. Символьні n-грами стійкі до різного розбиття коду на слова
 * ({@code "wrsb 7259 wb"} проти {@code "wrsb7259wb"}) і до закінчень слів.
 *
 * <p>Словник IDF будується на наборі нормалізованих назв ({@link #fit}); n-грама, якої не було в наборі,
 * отримує найбільшу вагу, як рідкісна.
 */
public final class TfIdfVectorizer {

    static final int GRAM = 3;

    private final Map<String, Double> idf;
    private final double unseenIdf;

    private TfIdfVectorizer(Map<String, Double> idf, double unseenIdf) {
        this.idf = idf;
        this.unseenIdf = unseenIdf;
    }

    /** Згладжений IDF: {@code ln((1 + N) / (1 + df)) + 1}. */
    public static TfIdfVectorizer fit(Collection<String> documents) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (String document : documents) {
            for (String gram : new HashSet<>(grams(document).keySet())) {
                documentFrequency.merge(gram, 1, Integer::sum);
            }
        }
        int n = documents.size();
        Map<String, Double> idf = new HashMap<>();
        documentFrequency.forEach((gram, df) -> idf.put(gram, Math.log((1.0 + n) / (1.0 + df)) + 1));
        return new TfIdfVectorizer(idf, Math.log(1.0 + n) + 1);
    }

    public SparseVector transform(String document) {
        Map<String, Integer> counts = grams(document);
        if (counts.isEmpty()) {
            return SparseVector.EMPTY;
        }
        Map<String, Double> weights = new HashMap<>();
        double norm = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            double w = e.getValue() * idf.getOrDefault(e.getKey(), unseenIdf);
            weights.put(e.getKey(), w);
            norm += w * w;
        }
        double length = Math.sqrt(norm);
        weights.replaceAll((gram, w) -> w / length);
        return new SparseVector(weights);
    }

    /** 3-грами рядка з пробілами по краях, щоб початок і кінець слів теж давали n-грами. */
    static Map<String, Integer> grams(String document) {
        Map<String, Integer> counts = new HashMap<>();
        if (document == null || document.isBlank()) {
            return counts;
        }
        String padded = " " + document.strip() + " ";
        for (int i = 0; i + GRAM <= padded.length(); i++) {
            counts.merge(padded.substring(i, i + GRAM), 1, Integer::sum);
        }
        return counts;
    }

}
