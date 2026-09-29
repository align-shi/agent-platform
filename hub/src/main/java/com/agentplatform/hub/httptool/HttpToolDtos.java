package com.agentplatform.hub.httptool;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public final class HttpToolDtos {

	private HttpToolDtos() {
	}

	public record ToolUpsert(
			String id,
			@NotBlank String name,
			@NotBlank String toolName,
			String description,
			@NotBlank String method,
			@NotBlank String url,
			Boolean enabled,
			List<HttpToolParam> parameters) {
	}

	public record UpsertRequest(
			@NotBlank String name,
			String description,
			String protocol,
			String authType,
			String authHeader,
			String secret,
			Integer timeoutMs,
			Boolean enabled,
			Boolean forwardCredentials,
			@Valid List<ToolUpsert> tools) {
	}

	public record TryRequest(Map<String, String> arguments, String secret) {
	}

	public record ToolView(
			String id,
			String name,
			String toolName,
			String description,
			String method,
			String url,
			boolean enabled,
			List<HttpToolParam> parameters) {
	}

	public record View(
			String id,
			String name,
			String description,
			String protocol,
			String authType,
			String authHeader,
			boolean secretConfigured,
			String keyLast4,
			int timeoutMs,
			boolean enabled,
			boolean forwardCredentials,
			List<ToolView> tools) {
	}

}
