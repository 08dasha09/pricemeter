package org.viewer.pricemeter.matching;

import org.viewer.pricemeter.catalog.service.MatchableOffer;
import org.viewer.pricemeter.matching.similarity.OfferText;

import java.util.List;

/**
 * Перехід від пропозицій каталогу до тексту для {@code MatchCandidateFactory}.
 */
public final class OfferTexts {

    private OfferTexts() {
    }

    public static List<OfferText> of(List<MatchableOffer> offers) {
        return offers.stream()
                .map(o -> new OfferText(o.id(), o.shopCode(), o.categoryCode(), o.title(), o.brandRaw(), o.mpn(),
                        o.gtin()))
                .toList();
    }

}
