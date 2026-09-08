package com.queqiao.server.repo;

import com.queqiao.server.entity.ReportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRecordRepository extends JpaRepository<ReportRecord, String>, JpaSpecificationExecutor<ReportRecord> {

    List<ReportRecord> findByOrderByCreatedAtDesc();
}
