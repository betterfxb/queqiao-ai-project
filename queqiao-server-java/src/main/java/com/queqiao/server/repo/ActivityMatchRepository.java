package com.queqiao.server.repo;

import com.queqiao.server.entity.ActivityMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivityMatchRepository extends JpaRepository<ActivityMatch, String> {

    @Query("SELECT m FROM ActivityMatch m WHERE m.activityId = :activityId AND m.aId = :aId AND m.bId = :bId")
    Optional<ActivityMatch> findByActivityIdAndAIdAndBId(@Param("activityId") String activityId,
                                                         @Param("aId") String aId,
                                                         @Param("bId") String bId);

    List<ActivityMatch> findByActivityIdOrderByCreatedAtDesc(String activityId);
}
