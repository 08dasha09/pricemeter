package org.viewer.pricemeter.catalog.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.viewer.pricemeter.catalog.domain.MatchStatus;
import org.viewer.pricemeter.catalog.domain.Offer;
import org.viewer.pricemeter.catalog.service.MatchableOffer;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long>, JpaSpecificationExecutor<Offer> {

    long countByActiveTrue();

    long countByShop_CodeAndActiveTrue(String shopCode);

    @Override
    @EntityGraph(attributePaths = {"shop", "category"})
    Page<Offer> findAll(Specification<Offer> spec, Pageable pageable);

    /** Пропозиція з магазином, категорією і товаром (для посилання на сторінку товару). */
    @EntityGraph(attributePaths = {"shop", "category", "product"})
    Optional<Offer> findWithShopAndCategoryById(Long id);

    Optional<Offer> findByShopIdAndExternalId(Long shopId, String externalId);

    @EntityGraph(attributePaths = {"shop", "category"})
    List<Offer> findAllByProductId(Long productId);

    @EntityGraph(attributePaths = {"shop", "category"})
    List<Offer> findAllByProductIdIn(Collection<Long> productIds);

    @EntityGraph(attributePaths = {"shop", "category"})
    List<Offer> findAllByIdIn(Collection<Long> ids);

    List<Offer> findAllByMatchStatus(MatchStatus matchStatus);

    List<Offer> findAllByShopIdAndActiveTrue(Long shopId);

    /** Активні пропозиції категорії магазину, крім {@code excludedIds}; спершу ті, що бачили найдавніше. */
    @Query("""
            select o from Offer o
            where o.shop.code = :shopCode and o.category.code = :categoryCode and o.active = true
              and o.id not in :excludedIds
            order by o.lastSeenAt asc, o.id asc
            """)
    List<Offer> findActiveExcept(@Param("shopCode") String shopCode, @Param("categoryCode") String categoryCode,
                                 @Param("excludedIds") Collection<Long> excludedIds, Pageable pageable);

    /** Усі пропозиції категорії (і неактивні — щоб товар не змінювався, поки пропозиція тимчасово зникла). */
    @Query("""
            select new org.viewer.pricemeter.catalog.service.MatchableOffer(
                o.id, s.code, c.code, o.rawTitle, o.brandRaw, o.mpn, o.gtin, p.id, o.matchStatus)
            from Offer o join o.shop s join o.category c left join o.product p
            where c.code = :categoryCode
            order by o.id
            """)
    List<MatchableOffer> findMatchable(@Param("categoryCode") String categoryCode);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Offer o set o.active = false
            where o.shop.id = :shopId and o.active = true and o.lastSeenAt < :threshold
            """)
    int deactivateNotSeenSince(@Param("shopId") Long shopId, @Param("threshold") Instant threshold);

}
