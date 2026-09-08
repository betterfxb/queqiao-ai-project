package com.queqiao.server.repo;

import com.queqiao.server.entity.ActivityCheckin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityCheckinRepository extends JpaRepository<ActivityCheckin, String> {

    List<ActivityCheckin> findByActivityIdOrderByAtDesc(String activityId);

    boolean existsByActivityIdAndMemberId(String activityId, String memberId);
}
