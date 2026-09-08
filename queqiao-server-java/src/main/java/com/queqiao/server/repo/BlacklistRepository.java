package com.queqiao.server.repo;

import com.queqiao.server.entity.Blacklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlacklistRepository extends JpaRepository<Blacklist, String>, JpaSpecificationExecutor<Blacklist> {

    List<Blacklist> findByMemberIdOrderByCreatedAtDesc(String memberId);

    Optional<Blacklist> findByMemberIdAndStatus(String memberId, String status);

    List<Blacklist> findByStatusOrderByCreatedAtDesc(String status);
}
