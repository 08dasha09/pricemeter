package org.viewer.pricemeter.crawler.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpFetcherTest {

    private HttpServer server;
    private String baseUrl;
    private final FakeTime time = new FakeTime();
    private final Map<String, AtomicInteger> hits = new ConcurrentHashMap<>();
    private final List<String> userAgents = new ArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void returnsBodyAndSendsUserAgent() {
        robots(404, "");
        respond("/product/1", 200, "<html>ok</html>");

        FetchedPage page = fetcher().fetch(uri("/product/1"));

        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).isEqualTo("<html>ok</html>");
        assertThat(userAgents).contains("TestBot/1.0");
    }

    @Test
    void retriesServerErrorWithExponentialBackoff() {
        robots(404, "");
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/flaky", ex -> {
            int n = calls.incrementAndGet();
            send(ex, n < 3 ? 503 : 200, "done");
        });

        FetchedPage page = fetcher().fetch(uri("/flaky"), Duration.ZERO);

        assertThat(page.body()).isEqualTo("done");
        assertThat(calls).hasValue(3);
        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
    }

    @Test
    void honorsRetryAfterHeader() {
        robots(404, "");
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/limited", ex -> {
            if (calls.incrementAndGet() == 1) {
                ex.getResponseHeaders().add("Retry-After", "7");
                send(ex, 429, "");
            } else {
                send(ex, 200, "ok");
            }
        });

        fetcher().fetch(uri("/limited"), Duration.ZERO);

        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(7));
    }

    @Test
    void doesNotRetryNotFound() {
        robots(404, "");
        respond("/gone", 404, "");

        assertThatThrownBy(() -> fetcher().fetch(uri("/gone")))
                .isInstanceOfSatisfying(FetchException.class, e -> {
                    assertThat(e.isNotFound()).isTrue();
                    assertThat(e.getStatusCode()).hasValue(404);
                });
        assertThat(hits.get("/gone")).hasValue(1);
    }

    @Test
    void givesUpAfterMaxAttempts() {
        robots(404, "");
        respond("/broken", 500, "");

        assertThatThrownBy(() -> fetcher().fetch(uri("/broken"), Duration.ZERO))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("500");
        assertThat(hits.get("/broken")).hasValue(3);
    }

    @Test
    void respectsRobotsTxtAndCachesIt() {
        robots(200, "User-agent: *\nDisallow: /private\n");
        respond("/private/1", 200, "secret");
        respond("/public/1", 200, "ok");
        HttpFetcher fetcher = fetcher();

        assertThatThrownBy(() -> fetcher.fetch(uri("/private/1"), Duration.ZERO))
                .isInstanceOf(RobotsDisallowedException.class);
        assertThat(fetcher.fetch(uri("/public/1"), Duration.ZERO).body()).isEqualTo("ok");

        assertThat(hits.get("/private/1")).isNull();
        assertThat(hits.get("/robots.txt")).hasValue(1);
    }

    @Test
    void refusesToCrawlWhenRobotsTxtFailsWithServerError() {
        robots(503, "");
        respond("/product/1", 200, "ok");

        assertThatThrownBy(() -> fetcher().fetch(uri("/product/1"), Duration.ZERO))
                .isInstanceOf(FetchException.class)
                .hasMessageContaining("robots.txt");
        assertThat(hits.get("/product/1")).isNull();
    }

    @Test
    void waitsMinIntervalBetweenRequestsToSameHost() {
        robots(404, "");
        respond("/a", 200, "a");
        respond("/b", 200, "b");
        HttpFetcher fetcher = fetcher();

        fetcher.fetch(uri("/a"), Duration.ofSeconds(3));
        fetcher.fetch(uri("/b"), Duration.ofSeconds(3));

        // robots.txt → /a → /b: кожен наступний запит чекає 3 секунди
        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(3), Duration.ofSeconds(3));
    }

    @Test
    void crawlDelayFromRobotsTxtExtendsInterval() {
        robots(200, "User-agent: *\nCrawl-delay: 10\n");
        respond("/a", 200, "a");
        respond("/b", 200, "b");
        HttpFetcher fetcher = fetcher();

        fetcher.fetch(uri("/a"), Duration.ofSeconds(1));
        fetcher.fetch(uri("/b"), Duration.ofSeconds(1));

        assertThat(time.sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(10));
    }

    private HttpFetcher fetcher() {
        CrawlerHttpProperties props = new CrawlerHttpProperties("TestBot/1.0", Duration.ofSeconds(2),
                Duration.ofSeconds(5), 3, Duration.ofSeconds(1), Duration.ZERO, Duration.ofSeconds(60), true);
        HttpClient client = HttpClient.newBuilder().connectTimeout(props.connectTimeout()).build();
        return new HttpFetcher(client, props, new HostRateLimiter(time, time), time, time);
    }

    private void robots(int status, String body) {
        respond("/robots.txt", status, body);
    }

    private void respond(String path, int status, String body) {
        server.createContext(path, ex -> send(ex, status, body));
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        hits.computeIfAbsent(exchange.getRequestURI().getPath(), p -> new AtomicInteger()).incrementAndGet();
        userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }

    /** Годинник, що просувається лише тоді, коли код «спить». */
    private static final class FakeTime extends Clock implements Sleeper {

        private Instant now = Instant.parse("2026-10-01T00:00:00Z");
        private final List<Duration> sleeps = new ArrayList<>();

        @Override
        public synchronized void sleep(Duration duration) {
            sleeps.add(duration);
            now = now.plus(duration);
        }

        @Override
        public synchronized Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

    }

}
