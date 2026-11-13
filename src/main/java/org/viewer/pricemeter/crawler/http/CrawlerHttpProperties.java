package org.viewer.pricemeter.crawler.http;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Налаштування HTTP-клієнта краулера ({@code crawler.http.*}).
 *
 * @param userAgent          заголовок User-Agent; частина до «/» — токен для robots.txt
 * @param connectTimeout     тайм-аут встановлення з'єднання
 * @param requestTimeout     тайм-аут отримання відповіді
 * @param maxAttempts        максимальна кількість спроб одного запиту
 * @param initialBackoff     затримка перед другою спробою; далі подвоюється
 * @param defaultMinInterval мінімальний інтервал між запитами до одного хоста за замовчуванням
 * @param maxRetryAfter      верхня межа для заголовка Retry-After
 * @param respectRobotsTxt   чи перевіряти robots.txt
 */
@ConfigurationProperties("crawler.http")
public record CrawlerHttpProperties(
        @DefaultValue("PricemeterBot/0.1 (educational price research)") String userAgent,
        @DefaultValue("10s") Duration connectTimeout,
        @DefaultValue("30s") Duration requestTimeout,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("2s") Duration initialBackoff,
        @DefaultValue("2s") Duration defaultMinInterval,
        @DefaultValue("60s") Duration maxRetryAfter,
        @DefaultValue("true") boolean respectRobotsTxt
) {

    public CrawlerHttpProperties {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("crawler.http.max-attempts must be >= 1");
        }
    }

}
