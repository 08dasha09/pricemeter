package org.viewer.pricemeter.analytics;

import java.util.Map;
import java.util.Optional;

/**
 * Вердикти для всіх пропозицій товару.
 *
 * @param market      поточний ринок товару
 * @param byOffer     вердикт кожної пропозиції (ключ — id пропозиції)
 * @param bestOfferId найдешевша пропозиція в наявності; {@code null} — ніде немає
 */
public record ProductVerdicts(ProductMarket market, Map<Long, Verdict> byOffer, Long bestOfferId) {

    /** Вердикт найнижчої ціни — підсумок для товару. */
    public Optional<Verdict> best() {
        return Optional.ofNullable(bestOfferId).map(byOffer::get);
    }

    public Optional<MarketOffer> bestOffer() {
        return market.offers().stream().filter(o -> bestOfferId != null && o.id() == bestOfferId).findFirst();
    }

}
