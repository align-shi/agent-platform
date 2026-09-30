package com.agentplatform.hub.workflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "workflow_node_runs", indexes = @Index(name = "idx_workflow_node_runs_run", columnList = "run_id"))
public class WorkflowNodeRunEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "run_id", nullable = false, length = 36)
	private String runId;

	@Column(name = "workflow_id", nullable = false, length = 36)
	private String workflowId;

	@Column(nullable = false)
	private int seq;

	@Column(name = "node_id", nullable = false, length = 64)
	private String nodeId;

	@Column(name = "node_type", nullable = false, length = 16)
	private String nodeType;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "input_text", columnDefinition = "TEXT")
	private String inputText;

	@Column(name = "output_text", columnDefinition = "TEXT")
	private String outputText;

	@Column(columnDefinition = "TEXT")
	private String error;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getRunId() {
		return runId;
	}

	public void setRunId(String runId) {
		this.runId = runId;
	}

	public String getWorkflowId() {
		return workflowId;
	}

	public void setWorkflowId(String workflowId) {
		this.workflowId = workflowId;
	}

	public int getSeq() {
		return seq;
	}

	public void setSeq(int seq) {
		this.seq = seq;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public String getNodeType() {
		return nodeType;
	}

	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getInputText() {
		return inputText;
	}

	public void setInputText(String inputText) {
		this.inputText = inputText;
	}

	public String getOutputText() {
		return outputText;
	}

	public void setOutputText(String outputText) {
		this.outputText = outputText;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

}
