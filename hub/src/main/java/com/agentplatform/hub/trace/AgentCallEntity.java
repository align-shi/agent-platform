package com.agentplatform.hub.trace;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_calls", indexes = {
		@Index(name = "idx_agent_calls_agent_started", columnList = "agent_id, started_at")
})
public class AgentCallEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "agent_id", nullable = false, length = 36)
	private String agentId;

	@Column(name = "agent_name", nullable = false)
	private String agentName;

	@Column(name = "conversation_id", nullable = false, length = 36)
	private String conversationId;

	@Column(name = "provider_id", length = 36)
	private String providerId;

	@Column(nullable = false)
	private String model;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "user_input", columnDefinition = "TEXT")
	private String userInput;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "latency_ms", nullable = false)
	private long latencyMs;

	@Column(nullable = false)
	private int rounds;

	@Column(name = "model_calls", nullable = false)
	private int modelCalls;

	@Column(name = "tool_calls", nullable = false)
	private int toolCalls;

	@Column(name = "mcp_calls", nullable = false)
	private int mcpCalls;

	@Column(name = "prompt_tokens", nullable = false)
	private int promptTokens;

	@Column(name = "completion_tokens", nullable = false)
	private int completionTokens;

	@Column(name = "total_tokens", nullable = false)
	private int totalTokens;

	@Column(columnDefinition = "TEXT")
	private String error;

	@Lob
	@Column(name = "trace_json", nullable = false)
	private String traceJson;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getAgentId() {
		return agentId;
	}

	public void setAgentId(String agentId) {
		this.agentId = agentId;
	}

	public String getAgentName() {
		return agentName;
	}

	public void setAgentName(String agentName) {
		this.agentName = agentName;
	}

	public String getConversationId() {
		return conversationId;
	}

	public void setConversationId(String conversationId) {
		this.conversationId = conversationId;
	}

	public String getProviderId() {
		return providerId;
	}

	public void setProviderId(String providerId) {
		this.providerId = providerId;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getUserInput() {
		return userInput;
	}

	public void setUserInput(String userInput) {
		this.userInput = userInput;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public long getLatencyMs() {
		return latencyMs;
	}

	public void setLatencyMs(long latencyMs) {
		this.latencyMs = latencyMs;
	}

	public int getRounds() {
		return rounds;
	}

	public void setRounds(int rounds) {
		this.rounds = rounds;
	}

	public int getModelCalls() {
		return modelCalls;
	}

	public void setModelCalls(int modelCalls) {
		this.modelCalls = modelCalls;
	}

	public int getToolCalls() {
		return toolCalls;
	}

	public void setToolCalls(int toolCalls) {
		this.toolCalls = toolCalls;
	}

	public int getMcpCalls() {
		return mcpCalls;
	}

	public void setMcpCalls(int mcpCalls) {
		this.mcpCalls = mcpCalls;
	}

	public int getPromptTokens() {
		return promptTokens;
	}

	public void setPromptTokens(int promptTokens) {
		this.promptTokens = promptTokens;
	}

	public int getCompletionTokens() {
		return completionTokens;
	}

	public void setCompletionTokens(int completionTokens) {
		this.completionTokens = completionTokens;
	}

	public int getTotalTokens() {
		return totalTokens;
	}

	public void setTotalTokens(int totalTokens) {
		this.totalTokens = totalTokens;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public String getTraceJson() {
		return traceJson;
	}

	public void setTraceJson(String traceJson) {
		this.traceJson = traceJson;
	}

}
