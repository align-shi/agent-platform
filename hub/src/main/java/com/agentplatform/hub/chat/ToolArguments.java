package com.agentplatform.hub.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ToolArguments {

	private static final Pattern STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

	private ToolArguments() {
	}

	static String string(String json, String field) {
		if (json == null || json.isBlank()) {
			return "";
		}
		Matcher matcher = STRING_FIELD.matcher(json);
		while (matcher.find()) {
			if (field.equals(matcher.group(1))) {
				return unescape(matcher.group(2));
			}
		}
		return "";
	}

	static List<String> strings(String json, String field) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		Pattern array = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)]", Pattern.DOTALL);
		Matcher matcher = array.matcher(json);
		if (!matcher.find()) {
			String single = string(json, field);
			return single.isBlank() ? List.of() : List.of(single);
		}
		List<String> values = new ArrayList<>();
		Matcher items = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(matcher.group(1));
		while (items.find()) {
			values.add(unescape(items.group(1)));
		}
		return values;
	}

	private static String unescape(String value) {
		return value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
	}

}
