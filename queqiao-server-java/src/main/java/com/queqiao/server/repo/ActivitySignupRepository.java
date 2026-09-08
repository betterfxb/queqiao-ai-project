package com.queqiao.server.repo;

import com.queqiao.server.entity.ActivitySignup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivitySignupRepository extends JpaRepository<ActivitySignup, Long>, JpaSpecificationExecutor<ActivitySignup> {

    List<ActivitySignup> findByActivityId(String activityId);

    long countByActivityId(String activityId);

    long countByActivityIdAndGender(String activityId, String gender);

    boolean existsByActivityIdAndMemberId(String activityId, String memberId);
}
