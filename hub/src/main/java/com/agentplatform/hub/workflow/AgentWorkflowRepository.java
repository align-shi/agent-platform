package com.agentplatform.hub.workflow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWorkflowRepository extends JpaRepository<AgentWorkflowEntity, String> {

	List<AgentWorkflowEntity> findByAgentIdOrderByWorkflowIdAsc(String agentId);

	void deleteByAgentId(String agentId);

	void deleteByWorkflowId(String workflowId);

}
