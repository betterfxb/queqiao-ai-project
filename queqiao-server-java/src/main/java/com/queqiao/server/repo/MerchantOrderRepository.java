package com.queqiao.server.repo;

import com.queqiao.server.entity.MerchantOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface MerchantOrderRepository extends JpaRepository<MerchantOrder, String>, JpaSpecificationExecutor<MerchantOrder> {

    List<MerchantOrder> findAllByOrderByCreatedAtDesc();

    List<MerchantOrder> findByStatusOrderByCreatedAtDesc(String status);

    List<MerchantOrder> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<MerchantOrder> findByMerchantIdOrderByCreatedAtDesc(String merchantId);

    List<MerchantOrder> findByPairId(String pairId);
}
