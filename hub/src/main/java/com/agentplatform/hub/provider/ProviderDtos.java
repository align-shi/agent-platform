package com.agentplatform.hub.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ProviderDtos {

	private ProviderDtos() {
	}

	public record UpsertRequest(
			@NotBlank String name,
			@NotBlank String baseUrl,
			String apiKey,
			@NotNull ProviderType type,
			String vendor,
			String defaultModel,
			Integer dimensions) {
	}

	public record View(
			String id,
			String name,
			String baseUrl,
			ProviderType type,
			String vendor,
			String defaultModel,
			Integer dimensions,
			String keyLast4,
			boolean keyConfigured) {
	}

}
