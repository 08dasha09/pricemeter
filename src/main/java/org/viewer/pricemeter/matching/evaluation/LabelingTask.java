package org.viewer.pricemeter.matching.evaluation;

import org.viewer.pricemeter.catalog.service.MatchableOffer;
import org.viewer.pricemeter.matching.normalize.ModelCode;
import org.viewer.pricemeter.matching.similarity.MatchCandidate;
import org.viewer.pricemeter.matching.similarity.MatchScore;

/**
 * Пара пропозицій для розмітки разом із поточною оцінкою системи.
 *
 * @param band          діапазон оцінки, з якого взято пару
 * @param unlabeledLeft скільки ще нерозмічених пар у цьому діапазоні (включно з цією)
 */
public record LabelingTask(
        String categoryCode,
        Side a,
        Side b,
        MatchScore score,
        ScoreBand band,
        int unlabeledLeft) {

    /**
     * Пропозиція пари.
     *
     * @param modelCode код моделі, який бачить зіставлення, або {@code null}
     * @param brand     канонічний бренд або {@code null}
     */
    public record Side(long offerId, String shopCode, String title, String modelCode, String brand) {

        static Side of(MatchableOffer offer, MatchCandidate candidate) {
            ModelCode code = candidate.modelCode();
            return new Side(offer.id(), offer.shopCode(), offer.title(), code != null ? code.full() : null,
                    candidate.brand());
        }

    }

}
