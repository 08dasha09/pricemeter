package org.viewer.pricemeter.matching.cluster;

import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.matching.similarity.MatchCandidate;

/**
 * Пропозиція для кластеризації разом із попереднім рішенням.
 *
 * @param status    {@link MatchStatus#MANUAL} — товар закріплено вручну; {@link MatchStatus#REJECTED} — пропозицію
 *                  не об'єднуємо автоматично ні з чим
 * @param productId поточний товар або {@code null}
 */
public record ClusterInput(MatchCandidate candidate, MatchStatus status, Long productId) {
}
