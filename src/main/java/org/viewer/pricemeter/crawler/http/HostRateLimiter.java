package org.viewer.pricemeter.crawler.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Обмежує частоту запитів до одного хоста. Кожен виклик атомарно резервує наступний
 * вільний слот, тому працює коректно і з паралельних потоків.
 */
public class HostRateLimiter {

    private final Clock clock;
    private final Sleeper sleeper;
    private final ConcurrentHashMap<String, Instant> nextFreeSlot = new ConcurrentHashMap<>();

    public HostRateLimiter(Clock clock, Sleeper sleeper) {
        this.clock = clock;
        this.sleeper = sleeper;
    }

    /**
     * Чекає, доки до хоста можна буде надіслати наступний запит.
     */
    public void acquire(String host, Duration minInterval) throws InterruptedException {
        Instant now = clock.instant();
        Instant[] reserved = new Instant[1];
        nextFreeSlot.compute(host, (h, slot) -> {
            Instant start = slot == null || slot.isBefore(now) ? now : slot;
            reserved[0] = start;
            return start.plus(minInterval);
        });
        Duration wait = Duration.between(now, reserved[0]);
        if (wait.isPositive()) {
            sleeper.sleep(wait);
        }
    }

}
