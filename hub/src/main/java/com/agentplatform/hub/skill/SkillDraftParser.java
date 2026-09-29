package com.agentplatform.hub.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class SkillDraftParser {

	static final int MAX_FILES = 8;
	static final int MAX_CHARS = 32_000;

	private SkillDraftParser() {
	}

	static Draft parse(String raw) {
		Map<String, Object> root = readObject(raw);
		String name = text(root.get("name"));
		String code = text(root.get("code")).toLowerCase();
		String description = text(root.get("description"));
		String body = text(root.get("body"));
		if (body.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型没有生成技能正文");
		}
		if (body.length() > MAX_CHARS) {
			body = body.substring(0, MAX_CHARS);
		}
		List<DraftFile> files = new ArrayList<>();
		List<String> skipped = new ArrayList<>();
		Map<String, DraftFile> accepted = new LinkedHashMap<>();
		Object nodes = root.get("files");
		if (nodes instanceof List<?> items) {
			for (Object item : items) {
				if (accepted.size() >= MAX_FILES) {
					skipped.add("超过 " + MAX_FILES + " 个文件，其余已忽略");
					break;
				}
				if (!(item instanceof Map<?, ?> map)) {
					continue;
				}
				String path = text(map.get("path"));
				String content = text(map.get("content"));
				if (path.isBlank()) {
					continue;
				}
				try {
					SkillPaths.assertFile(path);
				}
				catch (RuntimeException ex) {
					skipped.add(path);
					continue;
				}
				if ("SKILL.md".equals(path)) {
					skipped.add(path);
					continue;
				}
				if (content.length() > MAX_CHARS) {
					content = content.substring(0, MAX_CHARS);
				}
				accepted.put(path, new DraftFile(path, content));
			}
		}
		files.addAll(accepted.values());
		return new Draft(name, code, description, body, files, skipped);
	}

	private static Map<String, Object> readObject(String raw) {
		String json = unwrap(raw);
		int start = json.indexOf('{');
		int end = json.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型没有返回可解析的技能 JSON");
		}
		try {
			Map<String, Object> node = JsonParserFactory.getJsonParser().parseMap(json.substring(start, end + 1));
			if (node == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型没有返回可解析的技能 JSON");
			}
			return node;
		}
		catch (ResponseStatusException ex) {
			throw ex;
		}
		catch (RuntimeException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型没有返回可解析的技能 JSON");
		}
	}

	private static String unwrap(String raw) {
		String text = raw == null ? "" : raw.trim();
		if (text.startsWith("```")) {
			int newline = text.indexOf('\n');
			if (newline > 0) {
				text = text.substring(newline + 1);
			}
			int fence = text.lastIndexOf("```");
			if (fence >= 0) {
				text = text.substring(0, fence);
			}
		}
		return text.trim();
	}

	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value).trim();
	}

	record Draft(String name, String code, String description, String body, List<DraftFile> files, List<String> skipped) {
	}

	record DraftFile(String path, String content) {
	}

}
