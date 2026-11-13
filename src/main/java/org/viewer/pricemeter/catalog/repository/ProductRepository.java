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
import org.viewer.pricemeter.catalog.domain.Product;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findByGtin(String gtin);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    @EntityGraph(attributePaths = "category")
    List<Product> findWithCategoryByIdIn(Collection<Long> ids);

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /** Видаляє товари категорії, до яких не належить жодна пропозиція. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from Product p
            where p.category.id = :categoryId
              and not exists (select o.id from Offer o where o.product = p)
            """)
    int deleteWithoutOffers(@Param("categoryId") Long categoryId);

}
