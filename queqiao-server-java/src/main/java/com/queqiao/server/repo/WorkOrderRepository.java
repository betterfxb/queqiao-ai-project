package com.queqiao.server.repo;

import com.queqiao.server.entity.WorkOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkOrderRepository extends JpaRepository<WorkOrder, String>, JpaSpecificationExecutor<WorkOrder> {

    List<WorkOrder> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<WorkOrder> findByRiskTypeAndStatus(String riskType, String status);

    List<WorkOrder> findByStatusOrderByCreatedAtDesc(String status);
}
