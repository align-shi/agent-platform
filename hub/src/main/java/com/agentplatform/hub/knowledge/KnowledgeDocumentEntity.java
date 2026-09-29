package com.agentplatform.hub.knowledge;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocumentEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "knowledge_base_id", nullable = false, length = 36)
	private String knowledgeBaseId;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false, length = 16)
	private String status;

	@Column(name = "error_message", columnDefinition = "TEXT")
	private String errorMessage;

	@Column(name = "char_count", nullable = false)
	private int charCount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	void prepareInsert() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getKnowledgeBaseId() {
		return knowledgeBaseId;
	}

	public void setKnowledgeBaseId(String knowledgeBaseId) {
		this.knowledgeBaseId = knowledgeBaseId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public int getCharCount() {
		return charCount;
	}

	public void setCharCount(int charCount) {
		this.charCount = charCount;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
