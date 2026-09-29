package com.agentplatform.hub.chat;

import java.util.Map;

import org.springframework.boot.json.JsonParserFactory;

public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {

	public static final TokenUsage NONE = new TokenUsage(0, 0, 0);

	public boolean isEmpty() {
		return promptTokens == 0 && completionTokens == 0 && totalTokens == 0;
	}

	public static TokenUsage from(Object raw) {
		if (!(raw instanceof Map<?, ?> map)) {
			return NONE;
		}
		int prompt = number(map, "prompt_tokens", "promptTokens");
		int completion = number(map, "completion_tokens", "completionTokens");
		int total = number(map, "total_tokens", "totalTokens");
		if (total == 0) {
			total = prompt + completion;
		}
		return new TokenUsage(prompt, completion, total);
	}

	public static TokenUsage fromSseData(String data) {
		if (data == null || !data.contains("\"usage\"")) {
			return NONE;
		}
		try {
			Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(data);
			return from(root.get("usage"));
		}
		catch (RuntimeException ex) {
			return NONE;
		}
	}

	private static int number(Map<?, ?> map, String snake, String camel) {
		Object value = map.get(snake);
		if (!(value instanceof Number)) {
			value = map.get(camel);
		}
		if (value instanceof Number number) {
			return Math.max(0, number.intValue());
		}
		return 0;
	}

}
