package com.queqiao.server.repo;

import com.queqiao.server.entity.PointLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointLogRepository extends JpaRepository<PointLog, String>, JpaSpecificationExecutor<PointLog> {

    List<PointLog> findByMemberIdOrderByCreatedAtDesc(String memberId);

    boolean existsByMemberIdAndTypeAndRefId(String memberId, String type, String refId);
}
