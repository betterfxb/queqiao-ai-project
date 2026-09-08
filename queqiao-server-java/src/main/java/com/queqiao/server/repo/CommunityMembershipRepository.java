package com.queqiao.server.repo;

import com.queqiao.server.entity.CommunityMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CommunityMembershipRepository extends JpaRepository<CommunityMembership, String>,
        JpaSpecificationExecutor<CommunityMembership> {

    List<CommunityMembership> findByTierId(String tierId);

    List<CommunityMembership> findByMemberId(String memberId);

    List<CommunityMembership> findByCity(String city);

    Optional<CommunityMembership> findByOrderId(String orderId);
}
