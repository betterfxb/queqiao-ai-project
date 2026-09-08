package com.queqiao.server.repo;

import com.queqiao.server.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String>, JpaSpecificationExecutor<Conversation> {

    List<Conversation> findByOrderByLastAtDesc();

    List<Conversation> findByKind(String kind);

    boolean existsByDedupKey(String dedupKey);

    /** P0-10：按会员查已有会话，替代 findAll() 后内存过滤 */
    Optional<Conversation> findFirstByMemberIdOrderByCreatedAtAsc(String memberId);

    /** 按会员查会话（家长视角可见性用，按最近活跃排序） */
    List<Conversation> findByMemberIdOrderByLastAtDesc(String memberId);
}
