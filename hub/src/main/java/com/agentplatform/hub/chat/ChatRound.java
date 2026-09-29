package com.agentplatform.hub.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

record ChatRound(String content, List<ToolCall> toolCalls, TokenUsage usage) {

	ChatRound(String content, List<ToolCall> toolCalls) {
		this(content, toolCalls, TokenUsage.NONE);
	}

	record ToolCall(String id, String name, String arguments) {
	}

	boolean hasToolCalls() {
		return toolCalls != null && !toolCalls.isEmpty();
	}

	static ChatRound fromMessage(Map<?, ?> message) {
		String content = asString(message.get("content"));
		List<ToolCall> calls = new ArrayList<>();
		Object raw = message.get("tool_calls");
		if (raw instanceof List<?> list) {
			for (Object item : list) {
				if (!(item instanceof Map<?, ?> call)) {
					continue;
				}
				String id = asString(call.get("id"));
				if (id.isBlank()) {
					id = "call-" + UUID.randomUUID();
				}
				Map<?, ?> function = call.get("function") instanceof Map<?, ?> fn ? fn : Map.of();
				String name = asString(function.get("name"));
				if (name.isBlank()) {
					continue;
				}
				calls.add(new ToolCall(id, name, argumentsJson(function.get("arguments"))));
			}
		}
		return new ChatRound(content, List.copyOf(calls));
	}

	private static String argumentsJson(Object arguments) {
		if (arguments == null) {
			return "{}";
		}
		if (arguments instanceof String text) {
			return text.isBlank() ? "{}" : text;
		}
		return String.valueOf(arguments);
	}

	private static String asString(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

}
