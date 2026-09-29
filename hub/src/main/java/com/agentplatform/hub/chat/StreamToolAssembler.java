package com.agentplatform.hub.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.boot.json.JsonParserFactory;

/**
 * 把流式补全里的正文和 tool_calls 片段拼成一轮结果。
 * 正文片段按到达顺序返回，方便立刻转给页面。
 */
final class StreamToolAssembler {

	private final StringBuilder content = new StringBuilder();
	private final TreeMap<Integer, PartialCall> calls = new TreeMap<>();
	private TokenUsage usage = TokenUsage.NONE;

	String accept(String json) {
		if (json == null || json.isBlank() || "[DONE]".equals(json.trim())) {
			return "";
		}
		Map<String, Object> root;
		try {
			root = JsonParserFactory.getJsonParser().parseMap(json);
		}
		catch (RuntimeException ex) {
			return "";
		}
		TokenUsage found = TokenUsage.from(root.get("usage"));
		if (!found.isEmpty()) {
			usage = found;
		}
		Object choices = root.get("choices");
		if (!(choices instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> choice)) {
			return "";
		}
		Object delta = choice.get("delta");
		if (!(delta instanceof Map<?, ?> chunk)) {
			return "";
		}
		String fragment = text(chunk.get("content"));
		if (!fragment.isEmpty()) {
			content.append(fragment);
		}
		Object rawCalls = chunk.get("tool_calls");
		if (rawCalls instanceof List<?> items) {
			for (Object item : items) {
				if (item instanceof Map<?, ?> call) {
					mergeCall(call);
				}
			}
		}
		return fragment;
	}

	ChatRound toRound() {
		List<ChatRound.ToolCall> list = new ArrayList<>();
		for (PartialCall call : calls.values()) {
			String name = call.name.toString();
			if (name.isBlank()) {
				continue;
			}
			String id = call.id.toString();
			if (id.isBlank()) {
				id = "call-" + UUID.randomUUID();
			}
			String arguments = call.arguments.isEmpty() ? "{}" : call.arguments.toString();
			list.add(new ChatRound.ToolCall(id, name, arguments));
		}
		return new ChatRound(content.toString(), List.copyOf(list), usage);
	}

	private void mergeCall(Map<?, ?> call) {
		int index = 0;
		if (call.get("index") instanceof Number number) {
			index = number.intValue();
		}
		PartialCall partial = calls.computeIfAbsent(index, (key) -> new PartialCall());
		partial.id.append(text(call.get("id")));
		Object function = call.get("function");
		if (function instanceof Map<?, ?> map) {
			partial.name.append(text(map.get("name")));
			partial.arguments.append(text(map.get("arguments")));
		}
	}

	private static String text(Object value) {
		return value instanceof String text ? text : "";
	}

	private static final class PartialCall {
		private final StringBuilder id = new StringBuilder();
		private final StringBuilder name = new StringBuilder();
		private final StringBuilder arguments = new StringBuilder();
	}

}
