package org.viewer.pricemeter.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.viewer.pricemeter.user.domain.AppUser;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    boolean existsByEmail(String email);

}
