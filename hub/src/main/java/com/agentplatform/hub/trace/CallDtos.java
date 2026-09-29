package com.agentplatform.hub.trace;

import java.time.Instant;
import java.util.List;

public final class CallDtos {

	private CallDtos() {
	}

	public record Section(String key, String label, String text) {
	}

	public record Span(
			String id,
			String kind,
			String title,
			String summary,
			long offsetMs,
			long durationMs,
			String status,
			List<Section> sections,
			List<Span> children) {
	}

	public record Summary(
			String id,
			String agentId,
			String agentName,
			String conversationId,
			String model,
			String status,
			String userInput,
			Instant startedAt,
			long latencyMs,
			int rounds,
			int modelCalls,
			int toolCalls,
			int mcpCalls,
			int promptTokens,
			int completionTokens,
			int totalTokens,
			String error) {
	}

	public record Detail(Summary call, List<Span> spans) {
	}

}
