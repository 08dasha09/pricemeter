package org.viewer.pricemeter.catalog.service;

import org.viewer.pricemeter.catalog.domain.Offer;

/**
 * Результат upsert-у пропозиції.
 *
 * @param offer   збережена пропозиція
 * @param created {@code true}, якщо пропозицію створено вперше
 */
public record OfferUpsertResult(Offer offer, boolean created) {
}
