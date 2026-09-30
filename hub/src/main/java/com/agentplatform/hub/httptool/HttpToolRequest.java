package com.agentplatform.hub.httptool;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record HttpToolRequest(String method, URI uri, Map<String, String> headers, String body) {

	private static final Pattern PATH_TOKEN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)\\}");
	private static final List<String> RESERVED = List.of("load_skill", "read_skill_resource", "run_skill_script", "run_workflow");

	static String normalizeToolName(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.trim().toLowerCase(Locale.ROOT);
	}

	static void validateToolName(String toolName) {
		String name = normalizeToolName(toolName);
		if (!name.matches("[a-z][a-z0-9_]{0,63}")) {
			throw new IllegalArgumentException("工具名需为小写字母开头，仅含字母数字下划线");
		}
		if (RESERVED.contains(name)) {
			throw new IllegalArgumentException("工具名与内置技能工具冲突");
		}
	}

	static void validateUrl(String url) {
		if (url == null || url.isBlank()) {
			throw new IllegalArgumentException("URL 不能为空");
		}
		String trimmed = url.trim();
		if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
			throw new IllegalArgumentException("只支持 http/https 地址");
		}
	}

	static HttpToolRequest build(HttpToolEntity tool, Map<String, String> args) {
		List<HttpToolParam> params = tool.parameters();
		Map<String, String> values = args == null ? Map.of() : args;
		for (HttpToolParam param : params) {
			if (param.required() && blank(values.get(param.name()))) {
				throw new IllegalArgumentException("缺少必填参数 " + param.name());
			}
		}
		String url = fillPath(tool.getUrl().trim(), params, values);
		String query = queryString(params, values);
		if (!query.isEmpty()) {
			url += (url.contains("?") ? "&" : "?") + query;
		}
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Accept", "application/json, text/plain, */*");
		for (HttpToolParam param : params) {
			if ("header".equals(param.in()) && !blank(values.get(param.name()))) {
				headers.put(param.name(), values.get(param.name()));
			}
		}
		String method = tool.getMethod().trim().toUpperCase(Locale.ROOT);
		String body = null;
		if (!"GET".equals(method) && !"HEAD".equals(method) && !"DELETE".equals(method)) {
			Map<String, String> bodyValues = new LinkedHashMap<>();
			for (HttpToolParam param : params) {
				if ("body".equals(param.in()) && values.containsKey(param.name())) {
					bodyValues.put(param.name(), values.get(param.name()));
				}
			}
			if (!bodyValues.isEmpty()) {
				body = JsonLite.objectToJson(bodyValues, params);
				headers.put("Content-Type", "application/json");
			}
		}
		return new HttpToolRequest(method, URI.create(url), headers, body);
	}

	private static String fillPath(String url, List<HttpToolParam> params, Map<String, String> values) {
		Matcher matcher = PATH_TOKEN.matcher(url);
		StringBuffer filled = new StringBuffer();
		while (matcher.find()) {
			String name = matcher.group(1);
			String value = values.get(name);
			if (value == null) {
				value = "";
			}
			matcher.appendReplacement(filled, Matcher.quoteReplacement(encode(value)));
		}
		matcher.appendTail(filled);
		return filled.toString();
	}

	private static String queryString(List<HttpToolParam> params, Map<String, String> values) {
		List<String> parts = new ArrayList<>();
		for (HttpToolParam param : params) {
			if (!"query".equals(param.in()) || blank(values.get(param.name()))) {
				continue;
			}
			parts.add(encode(param.name()) + "=" + encode(values.get(param.name())));
		}
		return String.join("&", parts);
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}
