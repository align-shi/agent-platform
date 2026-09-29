package com.agentplatform.hub.agent;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "agents")
public class AgentEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(length = 64, unique = true)
	private String code;

	@Column(name = "system_prompt", columnDefinition = "TEXT")
	private String systemPrompt;

	@Column(name = "provider_id", nullable = false, length = 36)
	private String providerId;

	@Column(nullable = false)
	private String model;

	@Column(name = "memory_mode", length = 16)
	private String memoryMode;

	@Column(name = "summarize_when_tokens")
	private Integer summarizeWhenTokens;

	@Column(name = "keep_last_messages")
	private Integer keepLastMessages;

	@Column(name = "memory_facts", columnDefinition = "TEXT")
	private String memoryFacts;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getSystemPrompt() {
		return systemPrompt;
	}

	public void setSystemPrompt(String systemPrompt) {
		this.systemPrompt = systemPrompt;
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

	public String getMemoryMode() {
		return memoryMode;
	}

	public void setMemoryMode(String memoryMode) {
		this.memoryMode = memoryMode;
	}

	public MemoryMode resolvedMemoryMode() {
		return MemoryMode.from(memoryMode);
	}

	public Integer getSummarizeWhenTokens() {
		return summarizeWhenTokens;
	}

	public void setSummarizeWhenTokens(Integer summarizeWhenTokens) {
		this.summarizeWhenTokens = summarizeWhenTokens;
	}

	public Integer getKeepLastMessages() {
		return keepLastMessages;
	}

	public void setKeepLastMessages(Integer keepLastMessages) {
		this.keepLastMessages = keepLastMessages;
	}

	public String getMemoryFacts() {
		return memoryFacts;
	}

	public void setMemoryFacts(String memoryFacts) {
		this.memoryFacts = memoryFacts;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
