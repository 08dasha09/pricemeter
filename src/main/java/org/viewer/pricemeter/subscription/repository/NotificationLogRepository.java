package org.viewer.pricemeter.subscription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.viewer.pricemeter.subscription.domain.NotificationLog;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findTop20ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

}
