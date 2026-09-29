package com.agentplatform.hub.mcp;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMcpRepository extends JpaRepository<AgentMcpEntity, String> {

	List<AgentMcpEntity> findByAgentIdOrderByMcpServerIdAsc(String agentId);

	void deleteByAgentId(String agentId);

	void deleteByMcpServerId(String mcpServerId);

}
