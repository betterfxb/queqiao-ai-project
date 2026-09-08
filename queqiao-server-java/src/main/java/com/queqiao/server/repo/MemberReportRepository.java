package com.queqiao.server.repo;

import com.queqiao.server.entity.MemberReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MemberReportRepository extends JpaRepository<MemberReport, String>, JpaSpecificationExecutor<MemberReport> {

    List<MemberReport> findByStatusOrderByCreatedAtDesc(String status);

    List<MemberReport> findByTargetMemberIdOrderByCreatedAtDesc(String targetMemberId);

    long countByTargetMemberIdAndStatus(String targetMemberId, String status);
}
