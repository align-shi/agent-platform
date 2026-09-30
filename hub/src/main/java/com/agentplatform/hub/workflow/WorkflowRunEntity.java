package com.agentplatform.hub.workflow;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "workflow_runs", indexes = @Index(name = "idx_workflow_runs_wf", columnList = "workflow_id"))
public class WorkflowRunEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "workflow_id", nullable = false, length = 36)
	private String workflowId;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
	private String inputText;

	@Column(name = "output_text", columnDefinition = "TEXT")
	private String outputText;

	@Column(columnDefinition = "TEXT")
	private String error;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getWorkflowId() {
		return workflowId;
	}

	public void setWorkflowId(String workflowId) {
		this.workflowId = workflowId;
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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(Instant finishedAt) {
		this.finishedAt = finishedAt;
	}

}
