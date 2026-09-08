package com.queqiao.server.repo;

import com.queqiao.server.entity.VerifyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerifyTaskRepository extends JpaRepository<VerifyTask, String>, JpaSpecificationExecutor<VerifyTask> {

    List<VerifyTask> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<VerifyTask> findByStatusOrderByCreatedAtDesc(String status);

    List<VerifyTask> findByMemberIdAndType(String memberId, String type);
}
