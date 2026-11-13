package org.viewer.pricemeter.matching.similarity;

import org.viewer.pricemeter.matching.normalize.ModelCode;

/**
 * Пропозиція, підготовлена до порівняння: канонічні бренд і код, вектор нормалізованої назви.
 *
 * @param brand           канонічний бренд або {@code null}
 * @param modelCode       код моделі або {@code null}
 * @param gtin            штрихкод без ведучих нулів або {@code null}
 * @param normalizedTitle назва після {@code TitleNormalizer} — для пояснень і налагодження
 * @param titleVector     TF-IDF вектор нормалізованої назви
 */
public record MatchCandidate(
        long offerId,
        String shopCode,
        String categoryCode,
        String brand,
        ModelCode modelCode,
        String gtin,
        String normalizedTitle,
        SparseVector titleVector) {
}
