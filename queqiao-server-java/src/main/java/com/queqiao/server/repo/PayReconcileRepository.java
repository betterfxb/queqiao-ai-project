package com.queqiao.server.repo;

import com.queqiao.server.entity.PayReconcile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface PayReconcileRepository extends JpaRepository<PayReconcile, String> {

    List<PayReconcile> findByReconcileDateOrderByCreatedAtDesc(LocalDate date);

    List<PayReconcile> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    void deleteByReconcileDate(LocalDate date);

    long countByReconcileDate(LocalDate date);
}
