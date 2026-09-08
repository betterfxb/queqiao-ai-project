package com.queqiao.server.repo;

import com.queqiao.server.entity.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface MemberRepository extends JpaRepository<Member, String>, JpaSpecificationExecutor<Member> {

    List<Member> findByPhone(String phone);

    /** 按专属推荐码解析会员（2-B 裂变） */
    Optional<Member> findByReferralCode(String code);

    List<Member> findByAgent(String agent);

    long countByStatus(String status);

    long countByGender(String gender);

    long countByLevel(String level);

    long countByCreatedAtAfter(LocalDateTime t);

    List<Member> findByCreatedAtAfter(LocalDateTime t);

    List<Member> findTop20ByOrderByCreatedAtDesc();

    // —— 统计聚合下沉到 DB（P0-10 避免整表加载后内存统计）——

    long countByAgent(String agent);

    long countByAgentAndCreatedAtAfter(String agent, LocalDateTime t);

    long countByStatusIn(java.util.Collection<String> statuses);

    long countByAgentAndStatusIn(String agent, java.util.Collection<String> statuses);

    long countByLevelIn(java.util.Collection<String> levels);

    long countByAgentAndLevelIn(String agent, java.util.Collection<String> levels);

    long countByAgentAndGender(String agent, String gender);

    /**
     * 匹配候选池（P0-10）：条件全部下沉到 DB，并由 Pageable 限制条数，
     * 避免 findAll() 整表加载 + 内存过滤，同时控制发往 AI 的候选规模。
     */
    @Query("select m from Member m where m.id <> :id and m.gender is not null "
            + "and (:gender is null or m.gender <> :gender) "
            + "and (m.status is null or m.status <> '已暂停') "
            + "and (:agent is null or m.agent = :agent) "
            + "order by m.createdAt desc")
    List<Member> findMatchPool(@Param("id") String id,
                               @Param("gender") String gender,
                               @Param("agent") String agent,
                               Pageable page);
}
