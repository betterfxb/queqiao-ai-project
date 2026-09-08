package com.queqiao.server.repo;

import com.queqiao.server.entity.PairFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface PairFeedbackRepository extends JpaRepository<PairFeedback, String> {

    List<PairFeedback> findByPairIdOrderByCreatedAtDesc(String pairId);

    @Query("select coalesce(avg(f.satisfaction), 0) from PairFeedback f where f.pairId = ?1")
    Double avgSatisfaction(String pairId);

    long countByPairId(String pairId);

    /** 3-D 满意度闭环：低分反馈扫描（满意度非空的低分，按时间倒序） */
    List<PairFeedback> findBySatisfactionLessThanEqualAndCreatedAtAfterOrderByCreatedAtDesc(
            Integer satisfaction, LocalDateTime since);
}
