package com.agentplatform.hub.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "knowledge_chunks", indexes = @Index(name = "idx_chunk_base", columnList = "knowledge_base_id"))
public class KnowledgeChunkEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "knowledge_base_id", nullable = false, length = 36)
	private String knowledgeBaseId;

	@Column(name = "document_id", nullable = false, length = 36)
	private String documentId;

	@Column(name = "chunk_index", nullable = false)
	private int chunkIndex;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(nullable = false, columnDefinition = "LONGTEXT")
	private String embedding;

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

	public String getDocumentId() {
		return documentId;
	}

	public void setDocumentId(String documentId) {
		this.documentId = documentId;
	}

	public int getChunkIndex() {
		return chunkIndex;
	}

	public void setChunkIndex(int chunkIndex) {
		this.chunkIndex = chunkIndex;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getEmbedding() {
		return embedding;
	}

	public void setEmbedding(String embedding) {
		this.embedding = embedding;
	}

}
