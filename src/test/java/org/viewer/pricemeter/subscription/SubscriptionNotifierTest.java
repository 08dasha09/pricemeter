package org.viewer.pricemeter.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSendException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.viewer.pricemeter.TestcontainersConfiguration;
import org.viewer.pricemeter.analytics.PriceAnalyticsService;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.analytics.VerdictService;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.repository.OfferRepository;
import org.viewer.pricemeter.catalog.repository.ProductRepository;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.OfferUpsertCommand;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.fakediscount.FakeDiscountDetector;
import org.viewer.pricemeter.fakediscount.rule.InflatedOldPriceRule;
import org.viewer.pricemeter.fakediscount.rule.PromoNotBelowMarketRule;
import org.viewer.pricemeter.fakediscount.rule.SamePriceElsewhereRule;
import org.viewer.pricemeter.pricehistory.service.PriceHistoryService;
import org.viewer.pricemeter.pricehistory.service.PriceObservation;
import org.viewer.pricemeter.subscription.domain.NotificationLog;
import org.viewer.pricemeter.subscription.repository.NotificationLogRepository;
import org.viewer.pricemeter.subscription.repository.SubscriptionRepository;
import org.viewer.pricemeter.subscription.service.NotificationSender;
import org.viewer.pricemeter.subscription.service.SubscriptionNotifier;
import org.viewer.pricemeter.subscription.service.SubscriptionService;
import org.viewer.pricemeter.user.config.UserConfig;
import org.viewer.pricemeter.user.service.UserService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Сповіщення підписників на справжній БД: ціни, вердикт і оцінка знижки рахуються так само, як на сайті.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, TimeConfig.class, UserConfig.class, UserService.class,
        CatalogService.class, PriceHistoryService.class, ProductMarketService.class, PriceAnalyticsService.class,
        VerdictService.class, FakeDiscountDetector.class, PromoNotBelowMarketRule.class, InflatedOldPriceRule.class,
        SamePriceElsewhereRule.class, SubscriptionService.class, SubscriptionNotifier.class})
@TestPropertySource(properties = "app.base-url=https://pricemeter.test")
class SubscriptionNotifierTest {

    private static final String BUYER = "buyer@example.com";

    @MockitoBean
    private NotificationSender sender;

    @Autowired
    private SubscriptionNotifier notifier;

    @Autowired
    private SubscriptionService service;

    @Autowired
    private SubscriptionRepository subscriptions;

    @Autowired
    private NotificationLogRepository notifications;

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

    private final Instant subscribedAt = Instant.now().minus(3, ChronoUnit.HOURS);
    private Product lg;
    private Offer epicentr;

    @BeforeEach
    void setUp() {
        when(sender.channel()).thenReturn(NotificationLog.Channel.LOG);
        users.register(BUYER, "secret-123");
        lg = products.save(new Product(catalog.getCategory("WASHING_MACHINES"), "LG F2Y2HS5WE"));
        offer("MOYO", lg, "20000", null, subscribedAt);
        offer("CITRUS", lg, "20000", null, subscribedAt);
        epicentr = offer("EPICENTR", lg, "20000", null, subscribedAt);
    }

    @Test
    void notifiesAboutRealDropOnceAndRemembersNewReference() {
        service.subscribe(BUYER, lg.getId(), null);
        // Епіцентр знизив ціну без «старої»: на 15% дешевше за інші магазини
        price(epicentr, "17000", null);

        SubscriptionNotifier.NotifyReport report = notifier.notifySubscribers();

        assertThat(report.sent()).isEqualTo(1);
        verify(sender).send(eq(BUYER), eq("Ціна знизилась: LG F2Y2HS5WE — 17 000 ₴"), contains(
                "Зараз: 17 000 ₴ — Епіцентр (було 20 000 ₴, на 15% дешевше)"));
        verify(sender).send(anyString(), anyString(), contains("https://pricemeter.test/products/" + lg.getId()));
        NotificationLog logged = notifications.findAll().getFirst();
        assertThat(logged.getKind()).isEqualTo(NotificationLog.Kind.REAL_DROP);
        assertThat(logged.getPreviousPrice()).isEqualByComparingTo("20000");
        assertThat(logged.isDelivered()).isTrue();
        assertThat(subscriptions.findAll().getFirst().getLastNotifiedPrice()).isEqualByComparingTo("17000");

        // та сама ціна — повторно не пишемо
        assertThat(notifier.notifySubscribers().sent()).isZero();
    }

    @Test
    void notifiesWhenTargetReached() {
        service.subscribe(BUYER, lg.getId(), new BigDecimal("19500"));
        price(epicentr, "19400", null);

        notifier.notifySubscribers();

        verify(sender).send(eq(BUYER), startsWith("Ціна досягла бажаної: LG F2Y2HS5WE — 19 400 ₴"),
                contains("Бажана ціна: 19 500 ₴"));
        assertThat(notifications.findAll()).extracting(NotificationLog::getKind)
                .containsExactly(NotificationLog.Kind.TARGET_REACHED);
    }

    @Test
    void fakeDiscountIsNotRealDrop() {
        service.subscribe(BUYER, lg.getId(), null);
        // «−25%» від 26 000, але інші магазини теж знизили до 19 400 — на рівні ринку, стара ціна завищена
        offers.findAll().forEach(o -> price(o, "19400", o.getId().equals(epicentr.getId()) ? "26000" : null));

        assertThat(notifier.notifySubscribers().sent()).isZero();
        verify(sender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void failedMailIsLoggedAndRetriedLater() {
        service.subscribe(BUYER, lg.getId(), null);
        price(epicentr, "17000", null);
        doThrow(new MailSendException("SMTP down")).when(sender).send(anyString(), anyString(), anyString());

        SubscriptionNotifier.NotifyReport report = notifier.notifySubscribers();

        assertThat(report.failed()).isEqualTo(1);
        assertThat(notifications.findAll().getFirst().isDelivered()).isFalse();
        assertThat(notifications.findAll().getFirst().getError()).contains("SMTP down");
        // опорна ціна не змінилась — наступного разу спробуємо знову
        assertThat(subscriptions.findAll().getFirst().getLastNotifiedPrice()).isEqualByComparingTo("20000");
        assertThat(subscriptions.findAll().getFirst().getLastNotifiedAt()).isNull();
    }

    private Offer offer(String shop, Product product, String price, String oldPrice, Instant at) {
        Offer offer = catalog.upsertOffer(new OfferUpsertCommand(shop, "WASHING_MACHINES", shop + "-1",
                "https://shop/" + shop, "LG F2Y2HS5WE", null, null, null, at)).offer();
        offer.setProduct(product);
        offers.flush();
        history.record(offer.getId(), new PriceObservation(new BigDecimal(price),
                oldPrice != null ? new BigDecimal(oldPrice) : null, true, at));
        return offer;
    }

    private void price(Offer offer, String price, String oldPrice) {
        history.record(offer.getId(), new PriceObservation(new BigDecimal(price),
                oldPrice != null ? new BigDecimal(oldPrice) : null, true, subscribedAt.plus(Duration.ofHours(2))));
    }

}
