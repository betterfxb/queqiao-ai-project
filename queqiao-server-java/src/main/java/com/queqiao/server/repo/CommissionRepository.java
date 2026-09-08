package com.queqiao.server.repo;

import com.queqiao.server.entity.CommissionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRepository extends JpaRepository<CommissionRecord, String>, JpaSpecificationExecutor<CommissionRecord> {

    List<CommissionRecord> findByAgentName(String agentName);

    List<CommissionRecord> findByStatus(String status);

    List<CommissionRecord> findByAgentNameAndStatus(String agentName, String status);

    /** 幂等去重：同一笔订单的同一类型佣金只计提一次 */
    Optional<CommissionRecord> findByOrderIdAndType(String orderId, String type);

    /** 按订单聚合（修复/统计用） */
    List<CommissionRecord> findByOrderId(String orderId);

    List<CommissionRecord> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
