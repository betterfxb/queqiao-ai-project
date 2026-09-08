package com.queqiao.server.repo;

import com.queqiao.server.entity.AuditTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditTaskRepository extends JpaRepository<AuditTask, String>, JpaSpecificationExecutor<AuditTask> {

    List<AuditTask> findByStatus(String status);

    long countByStatus(String status);

    List<AuditTask> findByStatusOrderBySubmittedAtDesc(String status);

    /** P0-10：红娘数据范围下沉 DB，替代 findAll() 后内存过滤 */
    List<AuditTask> findByAgent(String agent);
}
