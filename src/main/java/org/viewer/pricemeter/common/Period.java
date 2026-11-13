package org.viewer.pricemeter.common;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Період аналізу історії цін.
 */
public enum Period {
    WEEK(Duration.ofDays(7)),
    MONTH(Duration.ofDays(30)),
    QUARTER(Duration.ofDays(90)),
    ALL(null);

    private final Duration length;

    Period(Duration length) {
        this.length = length;
    }

    /**
     * Початок періоду відносно {@code now}; порожньо для {@link #ALL}.
     */
    public Optional<Instant> startFrom(Instant now) {
        return length == null ? Optional.empty() : Optional.of(now.minus(length));
    }

}
