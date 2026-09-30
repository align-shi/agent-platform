package com.agentplatform.hub.workflow;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowNodeRunRepository extends JpaRepository<WorkflowNodeRunEntity, String> {

	List<WorkflowNodeRunEntity> findByRunIdOrderBySeqAsc(String runId);

	void deleteByWorkflowId(String workflowId);

}
