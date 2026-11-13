package org.viewer.pricemeter.matching.evaluation;

/**
 * Невпорядкована пара пропозицій у канонічному вигляді: {@code first < second}.
 */
public record OfferPair(long first, long second) {

    public OfferPair {
        if (first >= second) {
            throw new IllegalArgumentException("pair must be ordered: " + first + " < " + second);
        }
    }

    public static OfferPair of(long a, long b) {
        if (a == b) {
            throw new IllegalArgumentException("Пропозицію не можна порівняти саму з собою");
        }
        return a < b ? new OfferPair(a, b) : new OfferPair(b, a);
    }

}
