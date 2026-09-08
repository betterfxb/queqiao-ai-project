package com.queqiao.server.repo;

import com.queqiao.server.entity.FollowUp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FollowUpRepository extends JpaRepository<FollowUp, String> {

    List<FollowUp> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<FollowUp> findByMemberIdOrderByCreatedAtAsc(String memberId);

    long countByMemberId(String memberId);

    boolean existsByDedupKey(String dedupKey);
}
