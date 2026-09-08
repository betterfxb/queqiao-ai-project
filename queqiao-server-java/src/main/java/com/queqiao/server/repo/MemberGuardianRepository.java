package com.queqiao.server.repo;

import com.queqiao.server.entity.MemberGuardian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MemberGuardianRepository extends JpaRepository<MemberGuardian, String>, JpaSpecificationExecutor<MemberGuardian> {

    List<MemberGuardian> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<MemberGuardian> findByGuardianPhoneAndAuthStatus(String phone, String status);

    Optional<MemberGuardian> findByMemberIdAndGuardianPhone(String memberId, String phone);

    long countByMemberIdAndGuardianPhoneAndAuthStatus(String memberId, String phone, String status);
}
