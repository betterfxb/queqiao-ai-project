package com.queqiao.server.repo;

import com.queqiao.server.entity.OperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    List<OperationLog> findTop100ByOrderByCreatedAtDesc();

    List<OperationLog> findTop100ByTargetOrderByCreatedAtDesc(String target);
}
