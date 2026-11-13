package org.viewer.pricemeter.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.repository.ProductRepository;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.catalog.service.ProductAssignment;
import org.viewer.pricemeter.common.NotFoundException;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.pricehistory.service.PriceObservation;
import org.viewer.pricemeter.subscription.repository.SubscriptionRepository;
import org.viewer.pricemeter.subscription.service.SubscriptionService;
import org.viewer.pricemeter.subscription.service.SubscriptionService.SubscribedProduct;
import org.viewer.pricemeter.user.config.UserConfig;
import org.viewer.pricemeter.user.service.UserService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class, UserConfig.class, UserService.class,
        CatalogService.class, PriceHistoryService.class, ProductMarketService.class, SubscriptionService.class})
class SubscriptionServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-18T01:00:00Z");
    private static final String BUYER = "buyer@example.com";

    @Autowired
    private SubscriptionService service;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private UserService users;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private PriceHistoryService history;

    @Autowired
    private ProductRepository products;

    @Autowired
    private OfferRepository offers;

    private Product lg;

    @BeforeEach
    void setUp() {
        users.register(BUYER, "secret-123");
        lg = products.save(new Product(catalog.getCategory("WASHING_MACHINES"), "LG F2Y2HS5WE"));
    }

    @Test
    void subscribesChangesTargetAndUnsubscribes() {
        service.subscribe(BUYER, lg.getId(), null);
        assertThat(service.find(BUYER, lg.getId())).hasValueSatisfying(s -> assertThat(s.targetPrice()).isNull());

        service.subscribe(BUYER, lg.getId(), new BigDecimal("15000"));
        assertThat(subscriptions.count()).isEqualTo(1);
        assertThat(service.find(BUYER, lg.getId()))
                .hasValueSatisfying(s -> assertThat(s.targetPrice()).isEqualByComparingTo("15000"));

        service.unsubscribe(BUYER, lg.getId());
        assertThat(service.find(BUYER, lg.getId())).isEmpty();
    }

    @Test
    void rejectsUnknownProductAndNonPositiveTarget() {
        assertThatThrownBy(() -> service.subscribe(BUYER, 999_999L, null)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.subscribe(BUYER, lg.getId(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsSubscriptionsWithCurrentLowestPrice() {
        offer("MOYO", "M-1", lg, "21000");
        offer("CITRUS", "C-1", lg, "19999");
        service.subscribe(BUYER, lg.getId(), new BigDecimal("20000"));

        List<SubscribedProduct> list = service.list(BUYER);

        assertThat(list).singleElement().satisfies(s -> {
            assertThat(s.title()).isEqualTo("LG F2Y2HS5WE");
            assertThat(s.categoryName()).isNotBlank();
            assertThat(s.minPrice()).isEqualByComparingTo("19999");
            assertThat(s.shopCount()).isEqualTo(2);
            assertThat(s.targetReached()).isTrue();
        });
    }

    @Test
    void subscriptionFollowsProductMergedByMatching() {
        Product duplicate = products.save(new Product(catalog.getCategory("WASHING_MACHINES"), "LG F2Y2HS5WE біла"));
        Offer moyo = offer("MOYO", "M-1", lg, "21000");
        Offer citrus = offer("CITRUS", "C-1", duplicate, "20000");
        service.subscribe(BUYER, duplicate.getId(), new BigDecimal("18000"));

        // зіставлення об'єднало дубль з основним товаром — дубль видаляється
        catalog.applyMatching("WASHING_MACHINES", List.of(new ProductAssignment(lg.getId(), "LG F2Y2HS5WE", "LG",
                "F2Y2HS5WE", List.of(
                new ProductAssignment.OfferMatch(moyo.getId(), MatchStatus.AUTO, BigDecimal.ONE),
                new ProductAssignment.OfferMatch(citrus.getId(), MatchStatus.AUTO, BigDecimal.ONE)))));

        assertThat(products.findById(duplicate.getId())).isEmpty();
        assertThat(service.find(BUYER, lg.getId()))
                .hasValueSatisfying(s -> assertThat(s.targetPrice()).isEqualByComparingTo("18000"));
    }

    @Test
    void mergeKeepsOneSubscriptionWhenUserFollowedBoth() {
        Product duplicate = products.save(new Product(catalog.getCategory("WASHING_MACHINES"), "LG F2Y2HS5WE біла"));
        Offer moyo = offer("MOYO", "M-1", lg, "21000");
        Offer citrus = offer("CITRUS", "C-1", duplicate, "20000");
        service.subscribe(BUYER, lg.getId(), null);
        service.subscribe(BUYER, duplicate.getId(), new BigDecimal("18000"));

        catalog.applyMatching("WASHING_MACHINES", List.of(new ProductAssignment(lg.getId(), "LG F2Y2HS5WE", "LG",
                "F2Y2HS5WE", List.of(
                new ProductAssignment.OfferMatch(moyo.getId(), MatchStatus.AUTO, BigDecimal.ONE),
                new ProductAssignment.OfferMatch(citrus.getId(), MatchStatus.AUTO, BigDecimal.ONE)))));

        assertThat(subscriptions.findAll()).singleElement()
                .satisfies(s -> assertThat(s.getProductId()).isEqualTo(lg.getId()));
    }

    @Test
    void manualAssignmentMovesSubscriptionOfEmptiedProduct() {
        Product duplicate = products.save(new Product(catalog.getCategory("WASHING_MACHINES"), "LG F2Y2HS5WE біла"));
        offer("MOYO", "M-1", lg, "21000");
        Offer citrus = offer("CITRUS", "C-1", duplicate, "20000");
        service.subscribe(BUYER, duplicate.getId(), null);

        catalog.assignOfferManually(citrus.getId(), lg.getId());

        assertThat(service.find(BUYER, lg.getId())).isPresent();
        assertThat(service.find(BUYER, duplicate.getId())).isEmpty();
    }

    private Offer offer(String shop, String externalId, Product product, String price) {
        Offer offer = catalog.upsertOffer(new OfferUpsertCommand(shop, "WASHING_MACHINES", externalId,
                "https://shop/" + externalId, "LG F2Y2HS5WE " + externalId, null, null, null, T0)).offer();
        offer.setProduct(product);
        offers.flush();
        history.record(offer.getId(), new PriceObservation(new BigDecimal(price), null, true, T0));
        return offer;
    }

}
