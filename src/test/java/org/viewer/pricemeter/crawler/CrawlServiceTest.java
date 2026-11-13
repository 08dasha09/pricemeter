package org.viewer.pricemeter.crawler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.crawler.adapter.OfferParseException;
import org.viewer.pricemeter.crawler.adapter.OfferSkippedException;
import org.viewer.pricemeter.crawler.adapter.ScrapedOffer;
import org.viewer.pricemeter.crawler.adapter.ShopAdapter;
import org.viewer.pricemeter.crawler.http.FetchException;
import org.viewer.pricemeter.crawler.parser.Availability;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class, CatalogService.class, PriceHistoryService.class,
        OfferIngestService.class, CrawlService.class, CrawlServiceTest.FakeAdapterConfig.class})
class CrawlServiceTest {

    @Autowired
    private CrawlService crawlService;

    @Autowired
    private FakeAdapter adapter;

    @Autowired
    private OfferRepository offers;

    @Autowired
    private PriceHistoryService history;

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAdapterConfig {
        @Bean
        FakeAdapter fakeAdapter() {
            return new FakeAdapter();
        }
    }

    @BeforeEach
    void resetAdapter() {
        adapter.reset();
    }

    @Test
    void savesOffersAndContinuesAfterFailure() {
        adapter.prices.putAll(Map.of("1", "15999", "2", "21999"));
        adapter.broken.add("3");
        adapter.foreign.add("4");

        CrawlReport report = crawlService.crawlCategory("moyo", "washing_machines", 10);

        assertThat(report.discovered()).isEqualTo(4);
        assertThat(report.saved()).isEqualTo(2);
        assertThat(report.created()).isEqualTo(2);
        assertThat(report.priceChanges()).isEqualTo(2);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        assertThat(report.errors()).singleElement().asString().contains("/3.html");

        Offer saved = offers.findAll().stream().filter(o -> o.getExternalId().equals("1")).findFirst().orElseThrow();
        assertThat(saved.getCategory().getCode()).isEqualTo("WASHING_MACHINES");
        assertThat(saved.getMpn()).isEqualTo("MODEL-1");
        assertThat(history.getLatest(saved.getId()).orElseThrow().getPrice()).isEqualByComparingTo("15999");
    }

    @Test
    void repeatedCrawlOnlyCountsRealPriceChanges() {
        adapter.prices.putAll(Map.of("1", "15999", "2", "21999"));
        crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);

        adapter.prices.put("2", "19999");
        CrawlReport second = crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);

        assertThat(second.saved()).isEqualTo(2);
        assertThat(second.created()).isZero();
        assertThat(second.priceChanges()).isEqualTo(1);
    }

    @Test
    void soldOutPageUpdatesKnownOfferButSkipsNewOne() {
        adapter.prices.putAll(Map.of("1", "15999"));
        crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);

        // «1» розпродали; «5» — архівна сторінка, якої ми ніколи не бачили в продажу
        adapter.prices.remove("1");
        adapter.soldOut.addAll(Set.of("1", "5"));
        CrawlReport report = crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);

        assertThat(report.saved()).isEqualTo(1);
        assertThat(report.skipped()).isEqualTo(1);
        assertThat(report.failed()).isZero();
        assertThat(offers.findAll()).extracting(Offer::getExternalId).containsExactly("1");
        var latest = history.getLatest(offer("1").getId()).orElseThrow();
        assertThat(latest.isAvailable()).isFalse();
        assertThat(latest.getPrice()).isNull();
    }

    @Test
    void rejectsUnknownShopAndUnsupportedCategory() {
        assertThatThrownBy(() -> crawlService.crawlCategory("ROZETKA", "WASHING_MACHINES", 10))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> crawlService.crawlCategory("MOYO", "MICROWAVES", 10))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void refreshesKnownOffersMissingFromCategoryPages() {
        adapter.prices.putAll(Map.of("1", "15999", "2", "21999", "3", "9999"));
        crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);

        // «3» випав зі сторінок категорії, але сторінка товару жива й ціна змінилась
        adapter.prices.remove("3");
        adapter.delisted.put("3", "8999");
        CrawlReport report = crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);

        assertThat(report.discovered()).isEqualTo(2);
        assertThat(report.refreshed()).isEqualTo(1);
        assertThat(report.saved()).isEqualTo(3);
        assertThat(report.priceChanges()).isEqualTo(1);
        assertThat(report.deactivated()).isZero();
        assertThat(history.getLatest(offer("3").getId()).orElseThrow().getPrice()).isEqualByComparingTo("8999");
        assertThat(adapter.fetched).filteredOn(url -> url.endsWith("/3.html")).hasSize(2);
    }

    @Test
    void deactivatesKnownOffersWhosePagesAreGone() {
        adapter.prices.putAll(Map.of("1", "15999", "2", "21999"));
        crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);

        adapter.prices.remove("2");
        adapter.gone.add("2");
        CrawlReport report = crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);

        assertThat(report.deactivated()).isEqualTo(1);
        assertThat(report.failed()).isZero();
        assertThat(offer("2").isActive()).isFalse();
        assertThat(offer("1").isActive()).isTrue();

        // неактивні пропозиції повторно не перевіряються
        adapter.fetched.clear();
        assertThat(crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10).deactivated()).isZero();
        assertThat(adapter.fetched).noneMatch(url -> url.endsWith("/2.html"));
    }

    @Test
    void refreshesAtMostLimitKnownOffersStalestFirst() {
        adapter.prices.putAll(Map.of("1", "1000", "2", "2000", "3", "3000"));
        crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 10);
        // «1» бачили найдавніше
        adapter.prices.put("4", "4000");
        adapter.delisted.putAll(Map.of("1", "1000", "2", "2000", "3", "3000"));
        adapter.prices.keySet().removeAll(List.of("1", "2", "3"));

        CrawlReport report = crawlService.crawlCategory("MOYO", "WASHING_MACHINES", 2);

        assertThat(report.discovered()).isEqualTo(1);
        assertThat(report.refreshed()).isEqualTo(2);
        assertThat(adapter.fetched.subList(adapter.fetched.size() - 2, adapter.fetched.size()))
                .containsExactly("https://www.moyo.ua/ua/item/1.html", "https://www.moyo.ua/ua/item/2.html");
    }

    private Offer offer(String externalId) {
        return offers.findAll().stream().filter(o -> o.getExternalId().equals(externalId)).findFirst().orElseThrow();
    }

    /**
     * Адаптер без мережі: товари «1», «2», … з цінами з {@code prices}; ті, що в {@code broken}, падають,
     * а ті, що в {@code foreign}, належать іншій категорії й пропускаються. Товари з {@code delisted} не
     * видно на сторінках категорії, але їх сторінки відкриваються; сторінки з {@code gone} повертають 404.
     */
    static class FakeAdapter implements ShopAdapter {

        final Map<String, String> prices = new HashMap<>();
        final Set<String> broken = new java.util.HashSet<>();
        final Set<String> foreign = new java.util.HashSet<>();
        final Map<String, String> delisted = new HashMap<>();
        final Set<String> gone = new java.util.HashSet<>();
        /** Сторінка є, але товару немає в наявності й ціни немає. */
        final Set<String> soldOut = new java.util.HashSet<>();
        final List<String> fetched = new java.util.ArrayList<>();
        private long tick;

        void reset() {
            prices.clear();
            broken.clear();
            foreign.clear();
            delisted.clear();
            gone.clear();
            soldOut.clear();
            fetched.clear();
        }

        @Override
        public String shopCode() {
            return "MOYO";
        }

        @Override
        public Set<String> supportedCategories() {
            return Set.of("WASHING_MACHINES");
        }

        @Override
        public List<URI> discoverOfferUrls(String categoryCode, int limit) {
            return java.util.stream.Stream.of(prices.keySet(), broken, foreign, soldOut)
                    .flatMap(Set::stream)
                    .sorted()
                    .limit(limit)
                    .map(id -> URI.create("https://www.moyo.ua/ua/item/" + id + ".html"))
                    .toList();
        }

        @Override
        public ScrapedOffer fetchOffer(URI url) {
            fetched.add(url.toString());
            String id = url.getPath().replaceAll("\\D", "");
            if (gone.contains(id)) {
                throw new FetchException(url, 404);
            }
            if (broken.contains(id)) {
                throw new OfferParseException(url, "broken page");
            }
            if (foreign.contains(id)) {
                throw new OfferSkippedException(url, "other category");
            }
            // кожне спостереження трохи пізніше за попереднє
            Instant at = Instant.parse("2026-10-04T10:00:00Z").plusSeconds(++tick * 60);
            if (soldOut.contains(id)) {
                return new ScrapedOffer(id, url, "Product " + id, null, null, null, null, null, null,
                        Availability.OUT_OF_STOCK, Map.of(), at);
            }
            return new ScrapedOffer(id, url, "Product " + id, "Brand", null, "MODEL-" + id,
                    new BigDecimal(prices.getOrDefault(id, delisted.get(id))), null, "UAH", Availability.IN_STOCK,
                    Map.of(), at);
        }

    }

}
