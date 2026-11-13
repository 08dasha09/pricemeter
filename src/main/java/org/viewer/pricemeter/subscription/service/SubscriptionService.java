package org.viewer.pricemeter.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.analytics.ProductMarket;
import org.viewer.pricemeter.analytics.ProductMarketService;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.catalog.service.ProductsReplacedEvent;
import org.viewer.pricemeter.common.config.TimeConfig;
import org.viewer.pricemeter.subscription.domain.NotificationLog;
import org.viewer.pricemeter.subscription.domain.Subscription;
import org.viewer.pricemeter.subscription.repository.NotificationLogRepository;
import org.viewer.pricemeter.subscription.repository.SubscriptionRepository;
import org.viewer.pricemeter.user.service.UserService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Підписки користувачів на товари: підписатися (або змінити бажану ціну), відписатися, список з поточними цінами.
 * Коли зіставлення об'єднує товари, підписка переходить до товару-наступника.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final SubscriptionRepository subscriptions;
    private final NotificationLogRepository notifications;
    private final UserService users;
    private final CatalogService catalog;
    private final ProductMarketService markets;
    private final Clock clock;

    /**
     * Підписка на товар; повторна — лише змінює бажану ціну.
     *
     * @param targetPrice бажана ціна або {@code null}
     * @throws org.viewer.pricemeter.common.NotFoundException немає товару або користувача
     */
    @Transactional
    public void subscribe(String email, Long productId, BigDecimal targetPrice) {
        if (targetPrice != null && targetPrice.signum() <= 0) {
            throw new IllegalArgumentException("Target price must be positive: " + targetPrice);
        }
        catalog.getProduct(productId);
        Long userId = users.getUserId(email);
        subscriptions.findByUserIdAndProductId(userId, productId).ifPresentOrElse(
                existing -> existing.setTargetPrice(targetPrice),
                () -> {
                    Subscription created = new Subscription(userId, productId, targetPrice, clock.instant());
                    // опорна ціна: сповіщатимемо лише про нижчу за ту, що була на момент підписки
                    created.setLastNotifiedPrice(markets.market(productId).minPrice());
                    subscriptions.save(created);
                });
    }

    @Transactional
    public void unsubscribe(String email, Long productId) {
        subscriptions.findByUserIdAndProductId(users.getUserId(email), productId).ifPresent(subscriptions::delete);
    }

    /** Підписка користувача на товар, якщо є. */
    public Optional<SubscriptionStatus> find(String email, Long productId) {
        return subscriptions.findByUserIdAndProductId(users.getUserId(email), productId)
                .map(s -> new SubscriptionStatus(s.getTargetPrice()));
    }

    /** Підписки користувача від нових до старих, з поточною найнижчою ціною товару. */
    public List<SubscribedProduct> list(String email) {
        List<Subscription> own = subscriptions.findAllByUserIdOrderByCreatedAtDesc(users.getUserId(email));
        List<Long> productIds = own.stream().map(Subscription::getProductId).toList();
        Map<Long, Product> products = catalog.getProducts(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Map<Long, ProductMarket> byProduct = markets.markets(productIds);
        return own.stream()
                .filter(s -> products.containsKey(s.getProductId()))
                .map(s -> SubscribedProduct.of(products.get(s.getProductId()),
                        byProduct.getOrDefault(s.getProductId(), ProductMarket.EMPTY), s.getTargetPrice(),
                        LocalDateTime.ofInstant(s.getCreatedAt(), TimeConfig.ZONE)))
                .toList();
    }

    /** Останні 20 сповіщень користувача, від нових. */
    public List<SentNotification> recentNotifications(String email) {
        return notifications.findTop20ByUserIdOrderByCreatedAtDescIdDesc(users.getUserId(email)).stream()
                .map(n -> new SentNotification(n.getProductId(), n.getProductTitle(), n.getKind(), n.getPrice(),
                        n.getPreviousPrice(), n.getShopName(), n.getChannel(), n.isDelivered(),
                        LocalDateTime.ofInstant(n.getCreatedAt(), TimeConfig.ZONE)))
                .toList();
    }

    /**
     * Товари об'єднано: підписки переходять до наступника; якщо користувач уже стежить за наступником — стара
     * підписка зайва й видаляється.
     */
    @EventListener
    @Transactional
    public void onProductsReplaced(ProductsReplacedEvent event) {
        Map<Long, Long> replacements = event.replacements();
        for (Subscription subscription : subscriptions.findAllByProductIdIn(replacements.keySet())) {
            Long successor = replacements.get(subscription.getProductId());
            if (subscriptions.findByUserIdAndProductId(subscription.getUserId(), successor).isPresent()) {
                subscriptions.delete(subscription);
            } else {
                subscription.setProductId(successor);
            }
        }
        subscriptions.flush();
        log.debug("Subscriptions moved to successor products: {}", replacements);
    }

    /** Сповіщення в списку на сторінці підписок. */
    public record SentNotification(Long productId, String productTitle, NotificationLog.Kind kind, BigDecimal price,
                                   BigDecimal previousPrice, String shopName, NotificationLog.Channel channel,
                                   boolean delivered, LocalDateTime sentAt) {
    }

    /** @param targetPrice бажана ціна; {@code null} — лише реальне зниження */
    public record SubscriptionStatus(BigDecimal targetPrice) {
    }

    /**
     * Товар у списку підписок.
     *
     * @param minPrice    найнижча ціна в наявності зараз; {@code null} — ніде немає
     * @param targetPrice бажана ціна; {@code null} — лише реальне зниження
     */
    public record SubscribedProduct(Long productId, String title, String categoryName, BigDecimal minPrice,
                                    int purchasableCount, int shopCount, BigDecimal targetPrice,
                                    LocalDateTime subscribedAt) {

        static SubscribedProduct of(Product product, ProductMarket market, BigDecimal targetPrice,
                                    LocalDateTime subscribedAt) {
            return new SubscribedProduct(product.getId(), product.getTitle(), product.getCategory().getName(),
                    market.minPrice(), market.purchasableCount(), market.shops().size(), targetPrice, subscribedAt);
        }

        /** Найнижча ціна вже не вища за бажану. */
        public boolean targetReached() {
            return targetPrice != null && minPrice != null && minPrice.compareTo(targetPrice) <= 0;
        }
    }

}
