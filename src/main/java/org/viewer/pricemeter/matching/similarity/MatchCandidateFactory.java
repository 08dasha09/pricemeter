package org.viewer.pricemeter.matching.similarity;

import org.springframework.stereotype.Component;
import org.viewer.pricemeter.matching.normalize.BrandExtractor;
import org.viewer.pricemeter.matching.normalize.ModelCodeExtractor;
import org.viewer.pricemeter.matching.normalize.TitleNormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Готує пропозиції до порівняння: нормалізує назви, визначає бренд і код моделі, будує TF-IDF вектори
 * назв. Словник IDF рахується на всьому переданому наборі, тому пропозиції, які порівнюватимуться між
 * собою, треба готувати разом.
 */
@Component
public class MatchCandidateFactory {

    private final TitleNormalizer titleNormalizer;
    private final BrandExtractor brandExtractor;
    private final ModelCodeExtractor modelCodeExtractor;

    public MatchCandidateFactory(TitleNormalizer titleNormalizer, BrandExtractor brandExtractor,
                                 ModelCodeExtractor modelCodeExtractor) {
        this.titleNormalizer = titleNormalizer;
        this.brandExtractor = brandExtractor;
        this.modelCodeExtractor = modelCodeExtractor;
    }

    public List<MatchCandidate> build(List<OfferText> offers) {
        List<String> titles = offers.stream().map(offer -> titleNormalizer.normalize(offer.title())).toList();
        TfIdfVectorizer vectorizer = TfIdfVectorizer.fit(titles);

        List<MatchCandidate> candidates = new ArrayList<>(offers.size());
        for (int i = 0; i < offers.size(); i++) {
            OfferText offer = offers.get(i);
            String brand = brandExtractor.extract(offer.brandRaw(), offer.title()).orElse(null);
            candidates.add(new MatchCandidate(
                    offer.offerId(),
                    offer.shopCode(),
                    offer.categoryCode(),
                    brand,
                    modelCodeExtractor.extract(offer.title(), offer.mpn(), brand).orElse(null),
                    normalizeGtin(offer.gtin()),
                    titles.get(i),
                    vectorizer.transform(titles.get(i))));
        }
        return candidates;
    }

    /** EAN-13 і GTIN-14 одного товару відрізняються ведучим нулем. */
    private static String normalizeGtin(String gtin) {
        if (gtin == null || gtin.isBlank()) {
            return null;
        }
        String trimmed = gtin.strip().replaceFirst("^0+", "");
        return trimmed.isEmpty() ? null : trimmed;
    }

}
