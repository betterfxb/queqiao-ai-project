package com.queqiao.server.repo;

import com.queqiao.server.entity.CrmLead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrmLeadRepository extends JpaRepository<CrmLead, String>, JpaSpecificationExecutor<CrmLead> {

    List<CrmLead> findByStage(String stage);

    long countByStage(String stage);

    List<CrmLead> findByAgent(String agent);
}
