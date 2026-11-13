package org.viewer.pricemeter.crawler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertResult;
import org.viewer.pricemeter.crawler.adapter.OfferSkippedException;
import org.viewer.pricemeter.crawler.adapter.ScrapedOffer;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.pricehistory.service.PriceObservation;
import org.viewer.pricemeter.pricehistory.service.RecordOutcome;

/**
 * Зберігає зібрану пропозицію: каталог і історія цін оновлюються в одній транзакції.
 */
@Service
@RequiredArgsConstructor
public class OfferIngestService {

    private static final String SUPPORTED_CURRENCY = "UAH";

    private final CatalogService catalog;
    private final PriceHistoryService history;

    /**
     * Сторінку товару без ціни й наявності зберігаємо лише для відомої пропозиції — як новий стан «немає в
     * наявності». Нову таку пропозицію пропускаємо: фільтри брендів і карти сайту віддають сотні архівних
     * сторінок, які ніколи не продавались за нашого спостереження, і каталог заповнився б товарами без цін.
     *
     * @return {@code created} — пропозицію додано вперше; {@code outcome} — що сталося з історією ціни
     * @throws OfferSkippedException нова пропозиція без ціни й наявності
     */
    @Transactional
    public IngestResult ingest(String shopCode, String categoryCode, ScrapedOffer offer) {
        if (offer.currency() != null && !SUPPORTED_CURRENCY.equalsIgnoreCase(offer.currency())) {
            throw new IllegalArgumentException("Unsupported currency " + offer.currency() + " for " + offer.url());
        }
        PriceObservation observation = offer.toPriceObservation();
        if (observation.price() == null && !observation.available()
                && !catalog.offerExists(shopCode, offer.externalId())) {
            throw new OfferSkippedException(offer.url(), "Not on sale and never seen before");
        }
        OfferUpsertResult upsert = catalog.upsertOffer(offer.toUpsertCommand(shopCode, categoryCode));
        RecordOutcome outcome = history.record(upsert.offer().getId(), observation);
        return new IngestResult(upsert.offer().getId(), upsert.created(), outcome);
    }

    public record IngestResult(Long offerId, boolean created, RecordOutcome outcome) {
    }

}
