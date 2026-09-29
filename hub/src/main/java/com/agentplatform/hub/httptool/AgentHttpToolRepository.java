package com.agentplatform.hub.httptool;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentHttpToolRepository extends JpaRepository<AgentHttpToolEntity, String> {

	List<AgentHttpToolEntity> findByAgentIdOrderByHttpToolIdAsc(String agentId);

	void deleteByAgentId(String agentId);

	void deleteByHttpToolId(String httpToolId);

}
