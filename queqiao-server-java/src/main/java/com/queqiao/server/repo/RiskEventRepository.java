package com.queqiao.server.repo;

import com.queqiao.server.entity.RiskEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RiskEventRepository extends JpaRepository<RiskEvent, String>, JpaSpecificationExecutor<RiskEvent> {

    List<RiskEvent> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<RiskEvent> findByHandledOrderByCreatedAtDesc(boolean handled);

    List<RiskEvent> findByMemberIdAndType(String memberId, String type);

    List<RiskEvent> findByHandledAndTypeOrderByCreatedAtDesc(boolean handled, String type);
}
