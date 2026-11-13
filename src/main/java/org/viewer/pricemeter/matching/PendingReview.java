package org.viewer.pricemeter.matching;

import org.viewer.pricemeter.matching.similarity.MatchScore;

import java.util.List;

/**
 * Пропозиція, що чекає ручної перевірки, з найімовірнішими товарами, до яких вона може належати.
 *
 * @param modelCode   код моделі, як його розпізнано, або {@code null}
 * @param suggestions товари-кандидати, від найвищої оцінки
 */
public record PendingReview(
        long offerId,
        String shopCode,
        String categoryCode,
        String title,
        String modelCode,
        List<Suggestion> suggestions) {

    /**
     * @param offers пропозиції товару-кандидата
     */
    public record Suggestion(long productId, double score, MatchScore.Reason reason, List<Member> offers) {
    }

    public record Member(long offerId, String shopCode, String title) {
    }

}
