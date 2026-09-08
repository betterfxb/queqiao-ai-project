package com.queqiao.server.repo;

import com.queqiao.server.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, String>, JpaSpecificationExecutor<Coupon> {

    List<Coupon> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<Coupon> findByStatus(String status);

    Optional<Coupon> findByCode(String code);

    /** 按来源幂等查询（同一转介绍奖励只应有一张有效券） */
    List<Coupon> findBySourceIdAndSourceType(String sourceId, String sourceType);
}
