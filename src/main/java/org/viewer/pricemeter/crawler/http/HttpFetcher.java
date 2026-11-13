package org.viewer.pricemeter.crawler.http;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Єдина точка виходу краулера в мережу: перевірка robots.txt, обмеження частоти запитів
 * до хоста, повтори з експоненційною затримкою та зрозумілі помилки.
 */
@Slf4j
public class HttpFetcher {

    private static final Duration ROBOTS_TTL = Duration.ofHours(24);
    private static final Duration SLOW_RESPONSE = Duration.ofSeconds(10);

    private final HttpClient client;
    private final CrawlerHttpProperties props;
    private final HostRateLimiter rateLimiter;
    private final Sleeper sleeper;
    private final Clock clock;
    private final String agentToken;
    private final Map<String, CachedRobots> robotsCache = new ConcurrentHashMap<>();

    public HttpFetcher(HttpClient client, CrawlerHttpProperties props, HostRateLimiter rateLimiter,
                       Sleeper sleeper, Clock clock) {
        this.client = client;
        this.props = props;
        this.rateLimiter = rateLimiter;
        this.sleeper = sleeper;
        this.clock = clock;
        this.agentToken = props.userAgent().split("[/\\s]", 2)[0];
    }

    public FetchedPage fetch(URI uri) {
        return fetch(uri, props.defaultMinInterval());
    }

    /**
     * Отримує сторінку.
     *
     * @param minInterval мінімальний інтервал між запитами до цього хоста (напр. {@code shop.crawlDelayMs})
     * @throws RobotsDisallowedException якщо адресу заборонено robots.txt
     * @throws FetchException            якщо сторінку не вдалося отримати
     */
    public FetchedPage fetch(URI uri, Duration minInterval) {
        try {
            Duration interval = minInterval;
            if (props.respectRobotsTxt()) {
                RobotsTxt robots = robotsFor(uri, minInterval);
                if (!robots.isAllowed(pathAndQuery(uri))) {
                    throw new RobotsDisallowedException(uri);
                }
                Duration crawlDelay = robots.crawlDelay().orElse(Duration.ZERO);
                if (crawlDelay.compareTo(interval) > 0) {
                    interval = crawlDelay;
                }
            }
            return fetchWithRetries(uri, interval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FetchException(uri, "Interrupted", e);
        }
    }

    private FetchedPage fetchWithRetries(URI uri, Duration interval) throws InterruptedException {
        for (int attempt = 1; ; attempt++) {
            rateLimiter.acquire(hostKey(uri), interval);
            Duration retryDelay;
            try {
                long started = System.nanoTime();
                HttpResponse<String> response = client.send(request(uri), HttpResponse.BodyHandlers.ofString());
                Duration took = Duration.ofNanos(System.nanoTime() - started);
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    if (took.compareTo(SLOW_RESPONSE) > 0) {
                        log.warn("Slow response from {}: {} ms", uri, took.toMillis());
                    }
                    return new FetchedPage(response.uri(), status, response.body());
                }
                if (!isRetryable(status) || attempt >= props.maxAttempts()) {
                    throw new FetchException(uri, status);
                }
                retryDelay = retryAfter(response).orElse(backoff(attempt));
                log.info("HTTP {} for {}, retrying in {} (attempt {}/{})",
                        status, uri, retryDelay, attempt, props.maxAttempts());
            } catch (IOException e) {
                if (attempt >= props.maxAttempts()) {
                    throw new FetchException(uri, "Request failed after " + attempt + " attempts", e);
                }
                retryDelay = backoff(attempt);
                log.info("{} for {}, retrying in {} (attempt {}/{})",
                        e.toString(), uri, retryDelay, attempt, props.maxAttempts());
            }
            sleeper.sleep(retryDelay);
        }
    }

    private RobotsTxt robotsFor(URI uri, Duration minInterval) throws InterruptedException {
        String key = hostKey(uri);
        Instant now = clock.instant();
        CachedRobots cached = robotsCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.robots();
        }

        URI robotsUri = uri.resolve("/robots.txt");
        rateLimiter.acquire(key, minInterval);
        RobotsTxt robots;
        try {
            HttpResponse<String> response = client.send(request(robotsUri), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                robots = RobotsTxt.parse(response.body(), agentToken);
            } else if (status >= 400 && status < 500) {
                // RFC 9309: robots.txt недоступний (4xx) — обмежень немає
                robots = RobotsTxt.allowAll();
            } else {
                // 5xx: правила невідомі — не обходимо сайт, доки robots.txt не стане доступним
                throw new FetchException(robotsUri, status);
            }
        } catch (IOException e) {
            throw new FetchException(robotsUri, "robots.txt unavailable", e);
        }
        robotsCache.put(key, new CachedRobots(robots, now.plus(ROBOTS_TTL)));
        return robots;
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .GET()
                .timeout(props.requestTimeout())
                .header("User-Agent", props.userAgent())
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "uk-UA,uk;q=0.9,en;q=0.5")
                .build();
    }

    private static boolean isRetryable(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private Duration backoff(int attempt) {
        return props.initialBackoff().multipliedBy(1L << (attempt - 1));
    }

    private Optional<Duration> retryAfter(HttpResponse<?> response) {
        return response.headers().firstValue("Retry-After").flatMap(value -> {
            try {
                Duration delay = Duration.ofSeconds(Long.parseLong(value.strip()));
                return Optional.of(delay.compareTo(props.maxRetryAfter()) > 0 ? props.maxRetryAfter() : delay);
            } catch (NumberFormatException e) {
                return Optional.empty(); // формат HTTP-date не підтримуємо — звичайна затримка
            }
        });
    }

    private static String hostKey(URI uri) {
        return uri.getScheme() + "://" + uri.getAuthority();
    }

    private static String pathAndQuery(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private record CachedRobots(RobotsTxt robots, Instant expiresAt) {
    }

}
