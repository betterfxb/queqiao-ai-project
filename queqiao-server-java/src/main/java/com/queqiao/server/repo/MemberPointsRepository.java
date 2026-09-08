package com.queqiao.server.repo;

import com.queqiao.server.entity.MemberPoints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemberPointsRepository extends JpaRepository<MemberPoints, String> {

    Optional<MemberPoints> findByMemberId(String memberId);
}
