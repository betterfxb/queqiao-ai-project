package com.queqiao.server.repo;

import com.queqiao.server.entity.PartnerMerchant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PartnerMerchantRepository extends JpaRepository<PartnerMerchant, String>, JpaSpecificationExecutor<PartnerMerchant> {

    List<PartnerMerchant> findAllByOrderByCreatedAtDesc();

    List<PartnerMerchant> findByStatusOrderByCreatedAtDesc(String status);

    List<PartnerMerchant> findByCategoryAndStatus(String category, String status);
}
