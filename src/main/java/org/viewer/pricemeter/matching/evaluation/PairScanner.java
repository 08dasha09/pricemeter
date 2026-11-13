package org.viewer.pricemeter.matching.evaluation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.viewer.pricemeter.catalog.domain.Category;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.MatchableOffer;
import org.viewer.pricemeter.matching.OfferTexts;
import org.viewer.pricemeter.matching.similarity.MatchCandidate;
import org.viewer.pricemeter.matching.similarity.MatchCandidateFactory;
import org.viewer.pricemeter.matching.similarity.MatchScore;
import org.viewer.pricemeter.matching.similarity.SimilarityScorer;

import java.util.List;
import java.util.function.Consumer;

/**
 * Перебирає пари, які зіставлення справді порівнює: пропозиції <b>різних</b> магазинів однієї категорії без
 * конфлікту брендів (пари одного магазину ніколи не об'єднуються). Генеральна сукупність для розмітки й оцінки.
 */
@Component
@RequiredArgsConstructor
class PairScanner {

    private final CatalogService catalog;
    private final MatchCandidateFactory candidateFactory;
    private final SimilarityScorer scorer;

    /**
     * Пара з поточною оцінкою; IDF назв рахується на всій категорії, як під час зіставлення.
     */
    record ScoredPair(String categoryCode, MatchableOffer a, MatchableOffer b, MatchCandidate candidateA,
                      MatchCandidate candidateB, MatchScore score) {

        OfferPair pair() {
            return OfferPair.of(a.id(), b.id());
        }

    }

    void scan(Consumer<ScoredPair> consumer) {
        for (String categoryCode : catalog.getCategories().stream().map(Category::getCode).sorted().toList()) {
            List<MatchableOffer> offers = catalog.getMatchableOffers(categoryCode);
            List<MatchCandidate> candidates = candidateFactory.build(OfferTexts.of(offers));
            for (int i = 0; i < offers.size(); i++) {
                for (int j = i + 1; j < offers.size(); j++) {
                    MatchCandidate a = candidates.get(i);
                    MatchCandidate b = candidates.get(j);
                    if (!comparable(a, b)) {
                        continue;
                    }
                    MatchScore score = scorer.score(a, b);
                    if (score.reason() != MatchScore.Reason.BLOCKED) {
                        consumer.accept(new ScoredPair(categoryCode, offers.get(i), offers.get(j), a, b, score));
                    }
                }
            }
        }
    }

    /** Поточна оцінка однієї пари (з IDF її категорії). */
    MatchScore score(String categoryCode, OfferPair pair) {
        List<MatchableOffer> offers = catalog.getMatchableOffers(categoryCode);
        List<MatchCandidate> candidates = candidateFactory.build(OfferTexts.of(offers));
        MatchCandidate a = null;
        MatchCandidate b = null;
        for (MatchCandidate candidate : candidates) {
            if (candidate.offerId() == pair.first()) {
                a = candidate;
            } else if (candidate.offerId() == pair.second()) {
                b = candidate;
            }
        }
        if (a == null || b == null) {
            throw new IllegalArgumentException("Пропозиції пари немає в категорії " + categoryCode);
        }
        return scorer.score(a, b);
    }

    /** Різні магазини й бренди без конфлікту — дешева перевірка перед оцінкою. */
    private static boolean comparable(MatchCandidate a, MatchCandidate b) {
        return !a.shopCode().equals(b.shopCode())
                && (a.brand() == null || b.brand() == null || a.brand().equals(b.brand()));
    }

}
