package org.viewer.pricemeter.pricehistory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.viewer.pricemeter.pricehistory.domain.PriceSnapshot;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PriceSnapshotRepository extends JpaRepository<PriceSnapshot, Long> {

    Optional<PriceSnapshot> findTopByOfferIdOrderByValidFromDesc(Long offerId);

    List<PriceSnapshot> findAllByOfferIdOrderByValidFromAsc(Long offerId);

    /** Стан, що діяв на момент {@code instant}: останній інтервал, який почався раніше. */
    Optional<PriceSnapshot> findTopByOfferIdAndValidFromLessThanOrderByValidFromDesc(Long offerId, Instant instant);

    List<PriceSnapshot> findAllByOfferIdAndValidFromGreaterThanEqualOrderByValidFromAsc(Long offerId, Instant since);

    @Query("""
            select s from PriceSnapshot s
            where s.offerId in :offerIds
              and s.validFrom = (select max(s2.validFrom) from PriceSnapshot s2 where s2.offerId = s.offerId)
            """)
    List<PriceSnapshot> findLatestForOffers(@Param("offerIds") Collection<Long> offerIds);

    /** Пропозиції, у яких за останнім станом товар у наявності й заявлено стару ціну. */
    @Query("""
            select s.offerId from PriceSnapshot s
            where s.available = true and s.oldPrice is not null
              and s.validFrom = (select max(s2.validFrom) from PriceSnapshot s2 where s2.offerId = s.offerId)
            """)
    List<Long> findOfferIdsWithCurrentDiscount();

}
