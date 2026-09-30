package com.agentplatform.hub.agent;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public final class AgentDtos {

	private AgentDtos() {
	}

	public record UpsertRequest(
			@NotBlank String name,
			@NotBlank String code,
			String systemPrompt,
			@NotBlank String providerId,
			@NotBlank String model,
			List<String> skillIds,
			List<String> httpToolIds,
			List<String> mcpServerIds,
			List<String> knowledgeBaseIds,
			List<String> workflowIds,
			String memoryMode,
			Integer summarizeWhenTokens,
			Integer keepLastMessages) {
	}

	public record View(
			String id,
			String name,
			String code,
			String systemPrompt,
			String providerId,
			String providerName,
			String model,
			List<String> skillIds,
			List<String> httpToolIds,
			List<String> mcpServerIds,
			List<String> knowledgeBaseIds,
			List<String> workflowIds,
			String memoryMode,
			Integer summarizeWhenTokens,
			Integer keepLastMessages,
			String memoryFacts) {
	}

}
