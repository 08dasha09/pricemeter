package org.viewer.pricemeter.matching.cluster;

import org.viewer.pricemeter.catalog.domain.MatchStatus;

import java.util.List;

/**
 * Пропозиції, що вважаються одним товаром.
 */
public record OfferCluster(List<Member> members) {

    /**
     * @param status     нове рішення: {@code AUTO} — об'єднано автоматично або збігів немає (окремий товар);
     *                   {@code PENDING} — окремий товар, але є можливий збіг для ручної перевірки;
     *                   {@code MANUAL} / {@code REJECTED} — як було
     * @param confidence найкраща оцінка, що об'єднала пропозицію з кластером (для {@code PENDING} — оцінка
     *                   можливого збігу); {@code null}, якщо оцінки немає
     */
    public record Member(ClusterInput input, MatchStatus status, Double confidence) {
    }

}
