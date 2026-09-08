package com.queqiao.server.repo;

import com.queqiao.server.entity.RenewalReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RenewalReminderRepository extends JpaRepository<RenewalReminder, String> {

    Optional<RenewalReminder> findByOrderId(String orderId);

    List<RenewalReminder> findByStatus(String status);
}
