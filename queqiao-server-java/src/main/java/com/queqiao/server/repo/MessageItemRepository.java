package com.queqiao.server.repo;

import com.queqiao.server.entity.MessageItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageItemRepository extends JpaRepository<MessageItem, Long>, JpaSpecificationExecutor<MessageItem> {

    List<MessageItem> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    long countByConversationIdAndReadFlag(String conversationId, Boolean readFlag);
}
