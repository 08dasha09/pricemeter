package org.viewer.pricemeter.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.viewer.pricemeter.catalog.domain.Category;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

}
