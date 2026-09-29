package com.agentplatform.hub.mcp;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public final class RemoteMcpDtos {

	private RemoteMcpDtos() {
	}

	public record UpsertRequest(
			@NotBlank String name,
			String description,
			@NotBlank String url,
			String transport,
			String authType,
			String authHeader,
			String secret,
			Integer timeoutMs,
			Boolean enabled) {
	}

	public record TryRequest(String secret) {
	}

	public record ToolView(String name, String description) {
	}

	public record View(
			String id,
			String name,
			String description,
			String url,
			String transport,
			String authType,
			String authHeader,
			boolean secretConfigured,
			String keyLast4,
			int timeoutMs,
			boolean enabled,
			String serverName,
			String serverVersion,
			String protocolVersion,
			String lastError,
			String lastSyncedAt,
			List<ToolView> tools) {
	}

}
