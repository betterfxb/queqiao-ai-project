package com.queqiao.server.repo;

import com.queqiao.server.entity.MemberQuiz;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberQuizRepository extends JpaRepository<MemberQuiz, String> {

    List<MemberQuiz> findByMemberIdOrderByCreatedAtDesc(String memberId);

    Optional<MemberQuiz> findTopByMemberIdOrderByCreatedAtDesc(String memberId);

    Optional<MemberQuiz> findTopByMemberIdAndTemplateIdOrderByCreatedAtDesc(String memberId, String templateId);
}
