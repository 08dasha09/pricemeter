package org.viewer.pricemeter.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.viewer.pricemeter.subscription.domain.Subscription;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserIdAndProductId(Long userId, Long productId);

    List<Subscription> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    List<Subscription> findAllByProductIdIn(Collection<Long> productIds);

}
