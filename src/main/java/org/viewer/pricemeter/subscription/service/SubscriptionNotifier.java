package org.viewer.pricemeter.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.viewer.pricemeter.analytics.MarketOffer;
import org.viewer.pricemeter.analytics.PriceAnalyticsService;
import org.viewer.pricemeter.analytics.ProductVerdicts;
import org.viewer.pricemeter.analytics.Verdict;
import org.viewer.pricemeter.analytics.VerdictService;
import org.viewer.pricemeter.catalog.domain.Product;
import org.viewer.pricemeter.catalog.service.CatalogService;
import org.viewer.pricemeter.crawler.CrawlCompletedEvent;
import org.viewer.pricemeter.fakediscount.DiscountAssessment;
import org.viewer.pricemeter.fakediscount.FakeDiscountDetector;
import org.viewer.pricemeter.subscription.domain.NotificationLog;
import org.viewer.pricemeter.subscription.domain.Subscription;
import org.viewer.pricemeter.subscription.repository.NotificationLogRepository;
import org.viewer.pricemeter.subscription.repository.SubscriptionRepository;
import org.viewer.pricemeter.user.service.UserService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сповіщення підписників після кожного обходу (і зіставлення після нього): для кожного товару з підписками —
 * найнижча ціна в наявності, її вердикт і оцінка знижки → {@link NotificationRule} → лист або запис у лог, рядок у
 * {@code notification_log}. Невдалий лист повториться після наступного обходу.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionNotifier {

    private final SubscriptionRepository subscriptions;
    private final NotificationLogRepository notifications;
    private final UserService users;
    private final CatalogService catalog;
    private final VerdictService verdicts;
    private final FakeDiscountDetector discounts;
    private final NotificationSender sender;
    private final Clock clock;

    @Value("${app.base-url}")
    private String baseUrl;

    /** Після зіставлення ({@code MatchingOnCrawlListener} — {@code @Order(1)}): товари вже перераховано. */
    @EventListener
    @Order(2)
    public void onCrawlCompleted(CrawlCompletedEvent event) {
        try {
            NotifyReport report = notifySubscribers();
            log.info("Subscribers notified after crawl {}: {}", event.categoryCodes(), report);
        } catch (RuntimeException e) {
            log.error("Notifying subscribers after crawl {} failed", event.categoryCodes(), e);
        }
    }

    @Transactional
    public NotifyReport notifySubscribers() {
        Map<Long, List<Subscription>> byProduct = subscriptions.findAll().stream()
                .collect(Collectors.groupingBy(Subscription::getProductId));
        Map<Long, String> emails = users.getEmails(byProduct.values().stream()
                .flatMap(List::stream).map(Subscription::getUserId).collect(Collectors.toSet()));
        Map<Long, Product> products = catalog.getProducts(byProduct.keySet()).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        Instant now = clock.instant();
        int sent = 0;
        int failed = 0;
        for (Map.Entry<Long, List<Subscription>> entry : byProduct.entrySet()) {
            Product product = products.get(entry.getKey());
            if (product == null) {
                continue;
            }
            ProductVerdicts productVerdicts = verdicts.forProduct(product.getId(), PriceAnalyticsService.DEFAULT_PERIOD);
            Optional<MarketOffer> best = productVerdicts.bestOffer();
            BigDecimal price = best.map(MarketOffer::price).orElse(null);
            Verdict verdict = productVerdicts.best().orElse(null);
            DiscountAssessment.Level discount = best
                    .map(offer -> discounts.assess(productVerdicts.market()).get(offer.id()))
                    .map(DiscountAssessment::level)
                    .orElse(null);

            for (Subscription subscription : entry.getValue()) {
                BigDecimal reference = subscription.getLastNotifiedPrice();
                Optional<NotificationLog.Kind> kind = NotificationRule.decide(price, subscription.getTargetPrice(),
                        reference, subscription.getLastNotifiedAt(), verdict != null ? verdict.level() : null,
                        discount, now);
                if (kind.isEmpty()) {
                    if (reference == null && price != null) {
                        subscription.setLastNotifiedPrice(price);
                    }
                    continue;
                }
                if (deliver(subscription.getUserId(), emails.get(subscription.getUserId()), product, kind.get(), best.orElseThrow(), reference, subscription.getTargetPrice(),
                        verdict, now)) {
                    subscription.setLastNotifiedAt(now);
                    subscription.setLastNotifiedPrice(price);
                    sent++;
                } else {
                    failed++;
                }
            }
        }
        return new NotifyReport(byProduct.values().stream().mapToInt(List::size).sum(), sent, failed);
    }

    private boolean deliver(Long userId, String email, Product product, NotificationLog.Kind kind, MarketOffer best,
                            BigDecimal reference, BigDecimal target, Verdict verdict, Instant now) {
        String subject = (kind == NotificationLog.Kind.TARGET_REACHED ? "Ціна досягла бажаної: " : "Ціна знизилась: ")
                + product.getTitle() + " — " + money(best.price()) + " ₴";
        String text = text(product, best, reference, target, verdict);
        String error = null;
        try {
            sender.send(email, subject, text);
        } catch (RuntimeException e) {
            log.warn("Notification to {} about product {} failed: {}", email, product.getId(), e.getMessage());
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
        notifications.save(new NotificationLog(userId, product.getId(), product.getTitle(), kind,
                best.price(), reference, best.shopName(), sender.channel(), error == null, error, now));
        return error == null;
    }

    private String text(Product product, MarketOffer best, BigDecimal reference, BigDecimal target, Verdict verdict) {
        StringBuilder text = new StringBuilder(product.getTitle()).append("\n\n");
        text.append("Зараз: ").append(money(best.price())).append(" ₴ — ").append(best.shopName());
        if (reference != null && reference.compareTo(best.price()) > 0) {
            int percent = reference.subtract(best.price()).movePointRight(2)
                    .divide(reference, 0, RoundingMode.HALF_UP).intValue();
            text.append(" (було ").append(money(reference)).append(" ₴, на ").append(percent).append("% дешевше)");
        }
        text.append('\n');
        if (target != null) {
            text.append("Бажана ціна: ").append(money(target)).append(" ₴\n");
        }
        if (verdict != null && verdict.explanation() != null) {
            text.append("Оцінка: ").append(verdict.explanation()).append('\n');
        }
        text.append("\nТовар: ").append(baseUrl).append("/products/").append(product.getId()).append('\n');
        text.append("Підписки: ").append(baseUrl).append("/account/subscriptions\n");
        return text.toString();
    }

    /** «15 999», «15 999.5». */
    static String money(BigDecimal amount) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(' ');
        symbols.setDecimalSeparator('.');
        return new DecimalFormat("#,##0.##", symbols).format(amount);
    }

    /**
     * @param subscriptions скільки підписок перевірено
     * @param sent          скільки сповіщень доставлено (лист або запис у лог)
     * @param failed        скільки листів не надіслано
     */
    public record NotifyReport(int subscriptions, int sent, int failed) {
    }

}
