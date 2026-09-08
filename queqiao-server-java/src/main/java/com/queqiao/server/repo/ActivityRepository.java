package com.queqiao.server.repo;

import com.queqiao.server.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, String>, JpaSpecificationExecutor<Activity> {

    List<Activity> findByStatus(String status);

    List<Activity> findByDateBetween(java.time.LocalDate from, java.time.LocalDate to);

    List<Activity> findByOrderByDateDesc();

    // —— P0-10：统计下沉 DB，避免 findAll() 后内存聚合 ——

    long countByStatusIn(java.util.Collection<String> statuses);

    long countByDateGreaterThanEqualAndDateLessThan(java.time.LocalDate from, java.time.LocalDate to);

    /** 返回单行 [报名总数, 营收合计, 预期成对合计] */
    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(a.signed),0), coalesce(sum(a.revenue),0), coalesce(sum(a.expectedPairs),0) from Activity a")
    List<Object[]> statAggregateRow();
}
