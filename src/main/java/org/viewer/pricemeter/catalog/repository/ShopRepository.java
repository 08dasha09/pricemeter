package org.viewer.pricemeter.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.viewer.pricemeter.catalog.domain.Shop;

import java.util.List;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findByCode(String code);

    List<Shop> findAllByEnabledTrue();

}
