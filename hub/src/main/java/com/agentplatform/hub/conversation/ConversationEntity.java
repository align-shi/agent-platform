package com.agentplatform.hub.conversation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversations")
public class ConversationEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "agent_id", nullable = false, length = 36)
	private String agentId;

	@Column(nullable = false)
	private String title;

	@Column(columnDefinition = "TEXT")
	private String summary;

	@Column(name = "summary_up_to_seq")
	private Integer summaryUpToSeq;

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

	public String getAgentId() {
		return agentId;
	}

	public void setAgentId(String agentId) {
		this.agentId = agentId;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public Integer getSummaryUpToSeq() {
		return summaryUpToSeq;
	}

	public void setSummaryUpToSeq(Integer summaryUpToSeq) {
		this.summaryUpToSeq = summaryUpToSeq;
	}

	public int resolvedSummaryUpToSeq() {
		return summaryUpToSeq == null ? 0 : Math.max(0, summaryUpToSeq);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void touch() {
		updatedAt = Instant.now();
	}

}
