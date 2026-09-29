package com.agentplatform.hub.httptool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonLite {

	private static final Pattern OBJECT = Pattern.compile("\\{([^{}]*)\\}");
	private static final Pattern STRING = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
	private static final Pattern BOOL = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)");
	private static final Pattern NUMBER = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");

	private JsonLite() {
	}

	static String quote(String value) {
		if (value == null) {
			return "\"\"";
		}
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
	}

	static String unescape(String value) {
		return value.replace("\\\"", "\"").replace("\\\\", "\\").replace("\\n", "\n");
	}

	static String paramsToJson(List<HttpToolParam> params) {
		StringBuilder json = new StringBuilder("[");
		if (params != null) {
			for (int i = 0; i < params.size(); i++) {
				HttpToolParam param = params.get(i);
				if (i > 0) {
					json.append(',');
				}
				json.append("{\"name\":").append(quote(param.name()))
						.append(",\"in\":").append(quote(param.in()))
						.append(",\"type\":").append(quote(param.type()))
						.append(",\"required\":").append(param.required())
						.append(",\"description\":").append(quote(param.description() == null ? "" : param.description()))
						.append('}');
			}
		}
		return json.append(']').toString();
	}

	static List<HttpToolParam> paramsFromJson(String json) {
		List<HttpToolParam> params = new ArrayList<>();
		if (json == null || json.isBlank()) {
			return params;
		}
		Matcher objects = OBJECT.matcher(json);
		while (objects.find()) {
			Map<String, String> fields = object(objects.group(0));
			String name = fields.getOrDefault("name", "").trim();
			if (name.isBlank()) {
				continue;
			}
			params.add(new HttpToolParam(
					name,
					blankTo(fields.get("in"), "query"),
					blankTo(fields.get("type"), "string"),
					"true".equalsIgnoreCase(fields.get("required")),
					fields.getOrDefault("description", "")));
		}
		return params;
	}

	static Map<String, String> object(String json) {
		Map<String, String> values = new LinkedHashMap<>();
		if (json == null || json.isBlank()) {
			return values;
		}
		Matcher strings = STRING.matcher(json);
		while (strings.find()) {
			values.put(strings.group(1), unescape(strings.group(2)));
		}
		Matcher bools = BOOL.matcher(json);
		while (bools.find()) {
			values.putIfAbsent(bools.group(1), bools.group(2));
		}
		Matcher numbers = NUMBER.matcher(json);
		while (numbers.find()) {
			values.putIfAbsent(numbers.group(1), numbers.group(2));
		}
		return values;
	}

	static String objectToJson(Map<String, String> values, List<HttpToolParam> params) {
		Map<String, HttpToolParam> byName = new LinkedHashMap<>();
		if (params != null) {
			for (HttpToolParam param : params) {
				byName.put(param.name(), param);
			}
		}
		StringBuilder json = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, String> entry : values.entrySet()) {
			if (!first) {
				json.append(',');
			}
			first = false;
			json.append(quote(entry.getKey())).append(':');
			HttpToolParam param = byName.get(entry.getKey());
			String type = param == null ? "string" : param.type();
			json.append(typedValue(entry.getValue(), type));
		}
		return json.append('}').toString();
	}

	private static String typedValue(String value, String type) {
		if (value == null) {
			return "null";
		}
		if ("boolean".equals(type)) {
			return Boolean.parseBoolean(value) ? "true" : "false";
		}
		if ("number".equals(type) || "integer".equals(type)) {
			return value.isBlank() ? "0" : value;
		}
		return quote(value);
	}

	private static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

}
