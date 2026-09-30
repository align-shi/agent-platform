package com.agentplatform.hub.workflow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, String> {

	List<WorkflowRunEntity> findByWorkflowIdOrderByCreatedAtDesc(String workflowId);

	void deleteByWorkflowId(String workflowId);

}
