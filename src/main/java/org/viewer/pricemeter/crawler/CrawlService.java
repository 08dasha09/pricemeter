package org.viewer.pricemeter.crawler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.crawler.adapter.OfferSkippedException;
import org.viewer.pricemeter.crawler.adapter.ShopAdapter;
import org.viewer.pricemeter.crawler.http.FetchException;
import org.viewer.pricemeter.pricehistory.service.RecordOutcome;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Обхід категорії магазину: пошук сторінок товарів, їх розбір і збереження.
 * Помилка на одному товарі не зупиняє обхід.
 */
@Slf4j
@Service
public class CrawlService {

    private static final int MAX_REPORTED_ERRORS = 20;

    private final Map<String, ShopAdapter> adapters;
    private final OfferIngestService ingest;
    private final CatalogService catalog;
    private final Clock clock;

    public CrawlService(List<ShopAdapter> adapters, OfferIngestService ingest, CatalogService catalog, Clock clock) {
        this.adapters = adapters.stream().collect(Collectors.toMap(ShopAdapter::shopCode, Function.identity()));
        this.ingest = ingest;
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Магазин → категорії, які вміє обходити його адаптер. */
    public Map<String, List<String>> supportedCategories() {
        return adapters.values().stream().collect(Collectors.toMap(
                ShopAdapter::shopCode, a -> a.supportedCategories().stream().sorted().toList()));
    }

    /**
     * Обходить категорію: спершу товари, знайдені на сторінках категорії, потім — відомі активні пропозиції
     * цієї категорії, яких серед знайдених не було (до {@code limit} штук, спершу найдавніше бачені), за їх
     * збереженими адресами. Пропозиції, сторінки яких зникли (404 / 410), позначаються неактивними.
     */
    public CrawlReport crawlCategory(String shopCode, String categoryCode, int limit) {
        ShopAdapter adapter = adapters.get(shopCode.toUpperCase(Locale.ROOT));
        if (adapter == null) {
            throw NotFoundException.of("Shop adapter", shopCode);
        }
        String category = categoryCode.toUpperCase(Locale.ROOT);
        if (!adapter.supportedCategories().contains(category)) {
            throw NotFoundException.of("Category " + category + " for shop", adapter.shopCode());
        }

        Instant startedAt = clock.instant();
        Tally tally = new Tally();
        List<URI> urls = adapter.discoverOfferUrls(category, limit);
        for (URI url : urls) {
            try {
                tally.saved(ingest.ingest(adapter.shopCode(), category, adapter.fetchOffer(url)));
            } catch (OfferSkippedException e) {
                log.debug("Skipped {}", e.getMessage());
                tally.skipped++;
            } catch (RuntimeException e) {
                tally.failed(url, e);
            }
        }

        List<Offer> known = catalog.getActiveOffersExcept(adapter.shopCode(), category, tally.seenOfferIds, limit);
        for (Offer offer : known) {
            URI url = URI.create(offer.getUrl());
            try {
                tally.saved(ingest.ingest(adapter.shopCode(), category, adapter.fetchOffer(url)));
                tally.refreshed++;
            } catch (OfferSkippedException e) {
                log.debug("Skipped known offer {}", e.getMessage());
                tally.skipped++;
            } catch (FetchException e) {
                if (e.isNotFound()) {
                    log.info("Offer {} is gone ({}), deactivating", offer.getId(), e.getMessage());
                    catalog.deactivateOffer(offer.getId());
                    tally.deactivated++;
                } else {
                    tally.failed(url, e);
                }
            } catch (RuntimeException e) {
                tally.failed(url, e);
            }
        }

        CrawlReport report = new CrawlReport(adapter.shopCode(), category, urls.size(), tally.seenOfferIds.size(),
                tally.created, tally.priceChanges, tally.refreshed, tally.skipped, tally.deactivated,
                tally.errors.size(), tally.errors.stream().limit(MAX_REPORTED_ERRORS).toList(),
                startedAt, clock.instant());
        log.info("Crawl {} {} finished: {} discovered, {} saved ({} new, {} price changes, {} refreshed), "
                        + "{} skipped, {} deactivated, {} failed in {}",
                report.shopCode(), category, report.discovered(), report.saved(), report.created(),
                report.priceChanges(), report.refreshed(), report.skipped(), report.deactivated(), report.failed(),
                report.duration());
        return report;
    }

    /** Лічильники одного обходу. */
    private static final class Tally {

        final Set<Long> seenOfferIds = new HashSet<>();
        final List<String> errors = new ArrayList<>();
        int created;
        int priceChanges;
        int refreshed;
        int skipped;
        int deactivated;

        void saved(OfferIngestService.IngestResult result) {
            seenOfferIds.add(result.offerId());
            if (result.created()) {
                created++;
            }
            if (result.outcome() == RecordOutcome.NEW_STATE) {
                priceChanges++;
            }
        }

        void failed(URI url, RuntimeException e) {
            log.warn("Failed to crawl {}: {}", url, e.getMessage());
            errors.add(url + " — " + e.getMessage());
        }

    }

}
