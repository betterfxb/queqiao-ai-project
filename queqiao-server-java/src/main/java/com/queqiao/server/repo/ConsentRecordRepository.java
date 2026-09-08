package com.queqiao.server.repo;

import com.queqiao.server.entity.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, String>, JpaSpecificationExecutor<ConsentRecord> {

    List<ConsentRecord> findByMemberIdOrderByAcceptedAtDesc(String memberId);

    Optional<ConsentRecord> findByMemberIdAndDocType(String memberId, String docType);
}
