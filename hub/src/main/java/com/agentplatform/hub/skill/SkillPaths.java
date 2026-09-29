package com.agentplatform.hub.skill;

import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class SkillPaths {

	private SkillPaths() {
	}

	static String normalizeRelative(String relativePath) {
		if (relativePath == null || relativePath.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写路径");
		}
		String path = relativePath.trim().replace('\\', '/');
		if (path.startsWith("/") || path.contains(":") || path.contains("..")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请使用相对路径，不能包含 .. 或盘符");
		}
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		if (path.isBlank() || !path.matches("[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)*")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每一段只能包含字母、数字、点、下划线和中划线");
		}
		for (String part : path.split("/")) {
			if (part.startsWith(".")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每一段不能以点开头");
			}
		}
		return path;
	}

	static void assertDirectory(String relativePath) {
		String path = normalizeRelative(relativePath);
		rejectReserved(path);
		if ("SKILL.md".equals(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能把 SKILL.md 建成文件夹");
		}
		rejectScriptLayout(path, true);
	}

	static void assertFile(String relativePath) {
		String path = normalizeRelative(relativePath);
		rejectReserved(path);
		if ("scripts".equals(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scripts 是文件夹，不能建成文件");
		}
		rejectScriptLayout(path, false);
	}

	private static void rejectReserved(String path) {
		if (path.equals(".versions") || path.startsWith(".versions/")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能使用 .versions");
		}
	}

	private static void rejectScriptLayout(String path, boolean directory) {
		if ("scripts".equals(path)) {
			return;
		}
		if (!path.startsWith("scripts/")) {
			return;
		}
		String rest = path.substring("scripts/".length());
		if (directory || rest.contains("/")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scripts 目录下只能放脚本文件，不能再建子文件夹");
		}
	}

	static boolean isProtected(String relativePath) {
		String path = relativePath.replace('\\', '/');
		return path.equals(".versions") || path.startsWith(".versions/");
	}

	static Path resolve(Path skillDir, String relativePath) {
		String path = normalizeRelative(relativePath);
		Path file = skillDir.resolve(path).normalize();
		if (!file.startsWith(skillDir)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid skill file path");
		}
		return file;
	}

	static String posixRelative(Path skillDir, Path file) {
		return skillDir.relativize(file).toString().replace('\\', '/');
	}

	static boolean isScript(String relativePath) {
		String path = normalizeRelative(relativePath);
		if (!path.startsWith("scripts/") || path.equals("scripts/")) {
			return false;
		}
		return path.indexOf('/', "scripts/".length()) < 0;
	}

	static String scriptFileName(String script) {
		String path = normalizeRelative(script);
		if (!path.contains("/")) {
			path = "scripts/" + path;
		}
		if (!isScript(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Script must be a file in scripts/");
		}
		return path;
	}

}
