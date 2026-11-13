package org.viewer.pricemeter.matching.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchLabelRepository extends JpaRepository<MatchLabel, Long> {

    Optional<MatchLabel> findByOfferAIdAndOfferBId(long offerAId, long offerBId);

}
