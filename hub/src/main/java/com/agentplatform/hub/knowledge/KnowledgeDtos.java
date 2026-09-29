package com.agentplatform.hub.knowledge;

import jakarta.validation.constraints.NotBlank;

public final class KnowledgeDtos {

	private KnowledgeDtos() {
	}

	public record UpsertRequest(
			@NotBlank String name,
			String description,
			@NotBlank String embeddingProviderId,
			Integer topK,
			Integer chunkSize,
			Integer chunkOverlap) {
	}

	public record View(
			String id,
			String name,
			String description,
			String embeddingProviderId,
			String embeddingProviderName,
			String embeddingModel,
			Integer embeddingDim,
			int topK,
			int chunkSize,
			int chunkOverlap,
			int documentCount) {
	}

	public record DocumentRequest(
			@NotBlank String name,
			@NotBlank String content) {
	}

	public record DocumentView(
			String id,
			String knowledgeBaseId,
			String name,
			String status,
			String errorMessage,
			int charCount,
			int chunkCount) {
	}

}
