package com.agentplatform.hub.conversation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<MessageEntity, String> {

	List<MessageEntity> findByConversationIdOrderBySeqAsc(String conversationId);

	long countByConversationId(String conversationId);

	void deleteByConversationId(String conversationId);

}
