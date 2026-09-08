package com.queqiao.server.repo;

import com.queqiao.server.entity.CrmFollow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CrmFollowRepository extends JpaRepository<CrmFollow, Long>, JpaSpecificationExecutor<CrmFollow> {

    List<CrmFollow> findByLeadIdOrderByCreatedAtDesc(String leadId);
}
