package com.agentplatform.hub.skill;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class SkillZip {

	private static final int MAX_ENTRIES = 200;
	private static final int MAX_FILE_BYTES = 256 * 1024;
	private static final int MAX_TOTAL_BYTES = 8 * 1024 * 1024;
	private static final String SKILL_FILE = "SKILL.md";

	private SkillZip() {
	}

	record Entry(String path, boolean directory, String content) {
	}

	record Archive(String id, List<Entry> entries) {
	}

	static byte[] write(String skillId, List<Entry> entries) {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
			for (Entry entry : entries) {
				String name = skillId + "/" + entry.path() + (entry.directory() ? "/" : "");
				zip.putNextEntry(new ZipEntry(name));
				if (!entry.directory()) {
					zip.write((entry.content() == null ? "" : entry.content()).getBytes(StandardCharsets.UTF_8));
				}
				zip.closeEntry();
			}
		}
		catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法打包技能");
		}
		return buffer.toByteArray();
	}

	static Archive read(byte[] bytes, String originalFilename) {
		if (bytes == null || bytes.length == 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 zip 文件");
		}
		List<Raw> raw = new ArrayList<>();
		int total = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (raw.size() >= MAX_ENTRIES) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "技能包里的文件过多");
				}
				String name = cleanName(entry.getName());
				if (name.isEmpty() || junk(name)) {
					continue;
				}
				if (entry.isDirectory() || name.endsWith("/")) {
					raw.add(new Raw(stripTrailingSlash(name), true, ""));
					continue;
				}
				byte[] content = readLimited(zip, MAX_FILE_BYTES);
				total += content.length;
				if (total > MAX_TOTAL_BYTES) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "技能包解压后过大");
				}
				raw.add(new Raw(name, false, decode(content, name)));
			}
		}
		catch (ResponseStatusException ex) {
			throw ex;
		}
		catch (IOException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不是合法的 zip 文件");
		}
		if (raw.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip 里没有文件");
		}
		String root = skillRoot(raw);
		String prefix = root.equals(SKILL_FILE) ? "" : root.substring(0, root.length() - SKILL_FILE.length());
		String id = prefix.isEmpty() ? idFromFilename(originalFilename) : idFromFolder(prefix);
		List<Entry> entries = new ArrayList<>();
		for (Raw item : raw) {
			if (!prefix.isEmpty() && !item.name().equals(stripTrailingSlash(prefix)) && !item.name().startsWith(prefix)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip 里有技能目录以外的文件");
			}
			String relative = prefix.isEmpty() ? item.name() : item.name().substring(prefix.length());
			if (relative.isEmpty() || SkillPaths.isProtected(relative)) {
				continue;
			}
			String path = SkillPaths.normalizeRelative(relative);
			if (item.directory()) {
				SkillPaths.assertDirectory(path);
				entries.add(new Entry(path, true, ""));
			}
			else {
				SkillPaths.assertFile(path);
				entries.add(new Entry(path, false, item.content()));
			}
		}
		boolean hasSkill = entries.stream().anyMatch(entry -> !entry.directory() && SKILL_FILE.equals(entry.path()));
		if (!hasSkill) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip 里没有 SKILL.md");
		}
		return new Archive(id, entries);
	}

	private static String skillRoot(List<Raw> raw) {
		List<String> skills = raw.stream()
				.filter(item -> !item.directory())
				.map(Raw::name)
				.filter(name -> name.equals(SKILL_FILE) || name.endsWith("/" + SKILL_FILE))
				.sorted(Comparator.comparingInt(String::length))
				.toList();
		if (skills.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip 里没有 SKILL.md");
		}
		String root = skills.get(0);
		int depth = root.split("/").length;
		long sameDepth = skills.stream().filter(name -> name.split("/").length == depth).count();
		if (sameDepth > 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip 里有多个技能");
		}
		return root;
	}

	private static String idFromFolder(String prefix) {
		String folder = stripTrailingSlash(prefix);
		int slash = folder.lastIndexOf('/');
		String name = slash < 0 ? folder : folder.substring(slash + 1);
		return SkillService.normalizeId(name);
	}

	private static String idFromFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip 根目录是 SKILL.md 时，文件名需要是技能编码");
		}
		String name = originalFilename.replace('\\', '/');
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		if (name.toLowerCase().endsWith(".zip")) {
			name = name.substring(0, name.length() - 4);
		}
		return SkillService.normalizeId(name);
	}

	private static String cleanName(String name) {
		if (name == null) {
			return "";
		}
		String path = name.replace('\\', '/').trim();
		while (path.startsWith("./")) {
			path = path.substring(2);
		}
		if (path.startsWith("/") || path.contains(":") || path.contains("..")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "zip 里有不安全的路径");
		}
		return path;
	}

	private static boolean junk(String name) {
		return name.startsWith("__MACOSX/") || name.equals("__MACOSX") || name.endsWith("/.DS_Store") || name.equals(".DS_Store");
	}

	private static String stripTrailingSlash(String path) {
		return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
	}

	private static byte[] readLimited(ZipInputStream zip, int max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		int total = 0;
		int read;
		while ((read = zip.read(buffer)) >= 0) {
			total += read;
			if (total > max) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "技能包里有过大的文件");
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

	private static String decode(byte[] content, String name) {
		CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
				.onMalformedInput(CodingErrorAction.REPORT)
				.onUnmappableCharacter(CodingErrorAction.REPORT);
		try {
			return decoder.decode(java.nio.ByteBuffer.wrap(content)).toString();
		}
		catch (CharacterCodingException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持文本文件：" + name);
		}
	}

	private record Raw(String name, boolean directory, String content) {
	}

}
