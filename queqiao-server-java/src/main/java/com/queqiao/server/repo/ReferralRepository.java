package com.queqiao.server.repo;

import com.queqiao.server.entity.Referral;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, String>, JpaSpecificationExecutor<Referral> {

    List<Referral> findByStatusOrderByCreatedAtDesc(String status);

    List<Referral> findByReferrerIdOrderByCreatedAtDesc(String referrerId);

    Optional<Referral> findByReferrerIdAndRefereePhone(String referrerId, String refereePhone);

    List<Referral> findByRefereeMemberId(String refereeMemberId);

    List<Referral> findByRefereePhone(String refereePhone);

    /** 按订单聚合（修复/统计用） */
    List<Referral> findByOrderId(String orderId);
}
