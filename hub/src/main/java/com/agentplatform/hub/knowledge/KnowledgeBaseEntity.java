package com.agentplatform.hub.knowledge;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_bases")
public class KnowledgeBaseEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(name = "embedding_provider_id", nullable = false, length = 36)
	private String embeddingProviderId;

	@Column(name = "embedding_dim")
	private Integer embeddingDim;

	@Column(name = "top_k", nullable = false)
	private int topK;

	@Column(name = "chunk_size", nullable = false)
	private int chunkSize;

	@Column(name = "chunk_overlap", nullable = false)
	private int chunkOverlap;

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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getEmbeddingProviderId() {
		return embeddingProviderId;
	}

	public void setEmbeddingProviderId(String embeddingProviderId) {
		this.embeddingProviderId = embeddingProviderId;
	}

	public Integer getEmbeddingDim() {
		return embeddingDim;
	}

	public void setEmbeddingDim(Integer embeddingDim) {
		this.embeddingDim = embeddingDim;
	}

	public int getTopK() {
		return topK;
	}

	public void setTopK(int topK) {
		this.topK = topK;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	public int getChunkOverlap() {
		return chunkOverlap;
	}

	public void setChunkOverlap(int chunkOverlap) {
		this.chunkOverlap = chunkOverlap;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
