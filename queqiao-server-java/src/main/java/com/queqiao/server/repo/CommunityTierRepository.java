package com.queqiao.server.repo;

import com.queqiao.server.entity.CommunityTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface CommunityTierRepository extends JpaRepository<CommunityTier, String>, JpaSpecificationExecutor<CommunityTier> {

    List<CommunityTier> findAllByOrderBySortOrderAsc();

    List<CommunityTier> findByCityOrderBySortOrderAsc(String city);

    List<CommunityTier> findByStatusOrderBySortOrderAsc(String status);
}
