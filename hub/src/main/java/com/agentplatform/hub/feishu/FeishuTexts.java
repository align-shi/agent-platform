package com.agentplatform.hub.feishu;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class FeishuTexts {

	private static final JsonMapper MAPPER = new JsonMapper();
	private static final int MAX_PARTS = 5;

	private FeishuTexts() {
	}

	public static String userText(JsonNode contentNode, JsonNode mentions) {
		if (contentNode == null || contentNode.isNull() || contentNode.isMissingNode()) {
			return "";
		}
		String text = contentNode.isTextual()
				? textFromContentJson(contentNode.asText(""))
				: contentNode.path("text").asText("");
		if (mentions != null && mentions.isArray()) {
			for (JsonNode mention : mentions) {
				String key = mention.path("key").asText("");
				if (!key.isEmpty()) {
					text = text.replace(key, " ");
				}
			}
		}
		return text.replaceAll("\\s+", " ").trim();
	}

	public static List<String> chunks(String text, int limit) {
		String normalized = text == null ? "" : text.trim();
		if (normalized.isEmpty()) {
			return List.of("（没有生成回复）");
		}
		if (limit < 32) {
			limit = 32;
		}
		if (normalized.length() <= limit) {
			return List.of(normalized);
		}
		List<String> parts = new ArrayList<>();
		int start = 0;
		while (start < normalized.length() && parts.size() < MAX_PARTS) {
			int end = Math.min(start + limit, normalized.length());
			if (end < normalized.length()) {
				int breakAt = normalized.lastIndexOf('\n', end);
				if (breakAt > start) {
					end = breakAt;
				}
			}
			if (end <= start) {
				end = Math.min(start + limit, normalized.length());
			}
			String piece = normalized.substring(start, end).trim();
			if (!piece.isEmpty()) {
				parts.add(piece);
			}
			start = end;
		}
		if (start < normalized.length() && !parts.isEmpty()) {
			int last = parts.size() - 1;
			parts.set(last, parts.get(last) + "\n…（后面还有内容，已截断）");
		}
		return parts.isEmpty() ? List.of("（没有生成回复）") : parts;
	}

	private static String textFromContentJson(String contentJson) {
		String trimmed = contentJson == null ? "" : contentJson.trim();
		if (!trimmed.startsWith("{")) {
			return trimmed;
		}
		try {
			return MAPPER.readTree(trimmed).path("text").asText("");
		}
		catch (JacksonException ex) {
			return "";
		}
	}

}
