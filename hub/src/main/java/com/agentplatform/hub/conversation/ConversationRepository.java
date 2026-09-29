package com.agentplatform.hub.conversation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, String> {

	List<ConversationEntity> findByAgentIdOrderByUpdatedAtDesc(String agentId);

	void deleteByAgentId(String agentId);

}
