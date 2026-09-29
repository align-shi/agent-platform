package com.agentplatform.hub.trace;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentCallRepository extends JpaRepository<AgentCallEntity, String> {

	@Query("""
			select c.id, c.agentId, c.agentName, c.conversationId, c.model, c.status,
			       c.userInput, c.startedAt, c.latencyMs, c.rounds, c.modelCalls, c.toolCalls, c.mcpCalls,
			       c.promptTokens, c.completionTokens, c.totalTokens, c.error
			from AgentCallEntity c
			where (:agentId is null or :agentId = '' or c.agentId = :agentId)
			order by c.startedAt desc
			""")
	List<Object[]> listRows(@Param("agentId") String agentId, Pageable pageable);

	void deleteByConversationId(String conversationId);

	void deleteByAgentId(String agentId);

}
