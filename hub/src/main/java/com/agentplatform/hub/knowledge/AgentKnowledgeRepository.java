package com.agentplatform.hub.knowledge;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentKnowledgeRepository extends JpaRepository<AgentKnowledgeEntity, String> {

	List<AgentKnowledgeEntity> findByAgentIdOrderByKnowledgeBaseIdAsc(String agentId);

	void deleteByAgentId(String agentId);

	void deleteByKnowledgeBaseId(String knowledgeBaseId);

}
