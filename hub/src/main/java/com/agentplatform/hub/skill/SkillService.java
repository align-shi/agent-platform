package com.agentplatform.hub.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SkillService {

	private static final int MAX_FILE_BYTES = 256 * 1024;
	private static final int MAX_SCRIPT_OUTPUT = 32 * 1024;

	private final Path skillsDir;
	private final AgentSkillRepository agentSkills;
	private final Duration scriptTimeout;

	public SkillService(
			@Value("${agent-platform.skills-dir}") String skillsDir,
			AgentSkillRepository agentSkills,
			@Value("${agent-platform.skill-script-timeout-ms:15000}") long scriptTimeoutMs) throws IOException {
		this.skillsDir = Path.of(skillsDir).toAbsolutePath().normalize();
		this.agentSkills = agentSkills;
		this.scriptTimeout = Duration.ofMillis(scriptTimeoutMs);
		Files.createDirectories(this.skillsDir);
	}

	public List<SkillView> list() {
		try (Stream<Path> stream = Files.list(skillsDir)) {
			return stream
					.filter(Files::isDirectory)
					.map(Path::getFileName)
					.map(Path::toString)
					.sorted(Comparator.naturalOrder())
					.map(this::readOrSkip)
					.filter(item -> item != null)
					.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to list skills", ex);
		}
	}

	public SkillView require(String id) {
		Path file = skillFile(id);
		if (!Files.exists(file)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
		}
		try {
			return SkillMarkdown.parse(id, Files.readString(file)).withLatestVersion(latestVersionNumber(id));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read skill " + id, ex);
		}
	}

	public SkillView create(SkillView request) {
		String id = normalizeId(request.id());
		Path file = skillFile(id);
		if (Files.exists(file)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Skill already exists");
		}
		return write(id, request);
	}

	public SkillView update(String id, SkillView request) {
		require(id);
		return write(id, request);
	}

	@Transactional
	public void delete(String id) {
		Path dir = skillDir(id);
		if (!Files.exists(dir)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
		}
		agentSkills.deleteBySkillId(normalizeId(id));
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ex) {
					throw new IllegalStateException("Failed to delete " + path, ex);
				}
			});
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to delete skill " + id, ex);
		}
	}

	public List<String> listFiles(String id) {
		return listFiles(id, null);
	}

	public List<String> listFiles(String id, Integer version) {
		return listEntries(id, version, true);
	}

	public List<String> listTree(String id, Integer version) {
		return listEntries(id, version, false);
	}

	public SkillFileView readFile(String id, String relativePath) {
		return readFile(id, relativePath, null);
	}

	public SkillFileView readFile(String id, String relativePath, Integer version) {
		Path root = contentRoot(id, version);
		Path file = SkillPaths.resolve(root, relativePath);
		if (!Files.isRegularFile(file)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill file not found");
		}
		try {
			if (Files.size(file) > MAX_FILE_BYTES) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill file is too large");
			}
			return new SkillFileView(SkillPaths.posixRelative(root, file), Files.readString(file));
		}
		catch (ResponseStatusException ex) {
			throw ex;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to read " + relativePath, ex);
		}
	}

	public SkillFileView writeFile(String id, String relativePath, String content) {
		require(id);
		SkillPaths.assertFile(relativePath);
		Path file = SkillPaths.resolve(skillDir(id), relativePath);
		try {
			Files.createDirectories(file.getParent());
			byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
			if (bytes.length > MAX_FILE_BYTES) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill file is too large");
			}
			Files.write(file, bytes);
			if ("SKILL.md".equals(SkillPaths.posixRelative(skillDir(id), file))) {
				return readFile(id, "SKILL.md");
			}
			return new SkillFileView(SkillPaths.posixRelative(skillDir(id), file), content == null ? "" : content);
		}
		catch (ResponseStatusException ex) {
			throw ex;
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write " + relativePath, ex);
		}
	}

	public void deleteFile(String id, String relativePath) {
		String path = SkillPaths.normalizeRelative(relativePath);
		if ("SKILL.md".equals(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete SKILL.md; delete the skill instead");
		}
		Path target = SkillPaths.resolve(skillDir(id), path);
		if (!Files.exists(target)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill file not found");
		}
		deleteRecursively(target);
	}

	public SkillFileView renameFile(String id, String from, String to) {
		require(id);
		String sourcePath = SkillPaths.normalizeRelative(from);
		String destPath = SkillPaths.normalizeRelative(to);
		if ("SKILL.md".equals(sourcePath) || "SKILL.md".equals(destPath)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot rename SKILL.md");
		}
		Path source = SkillPaths.resolve(skillDir(id), sourcePath);
		Path dest = SkillPaths.resolve(skillDir(id), destPath);
		if (!Files.exists(source)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill file not found");
		}
		if (Files.exists(dest)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Target already exists");
		}
		try {
			Files.createDirectories(dest.getParent());
			Files.move(source, dest);
			if (Files.isRegularFile(dest)) {
				return readFile(id, destPath);
			}
			return new SkillFileView(destPath, "");
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to rename " + sourcePath, ex);
		}
	}

	public void createDirectory(String id, String relativePath) {
		require(id);
		SkillPaths.assertDirectory(relativePath);
		Path dir = SkillPaths.resolve(skillDir(id), relativePath);
		try {
			Files.createDirectories(dir);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to create directory " + relativePath, ex);
		}
	}

	public List<SkillVersionView> listVersions(String id) {
		require(id);
		Path versions = versionsRoot(id);
		if (!Files.isDirectory(versions)) {
			return List.of();
		}
		try (Stream<Path> stream = Files.list(versions)) {
			return stream
					.filter(Files::isDirectory)
					.map(Path::getFileName)
					.map(Path::toString)
					.filter(name -> name.matches("[0-9]+"))
					.map(Integer::parseInt)
					.sorted(Comparator.reverseOrder())
					.map(version -> toVersionView(id, version))
					.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to list versions", ex);
		}
	}

	public SkillVersionView publishVersion(String id) {
		require(id);
		int version = nextVersion(id);
		Path dest = versionsRoot(id).resolve(String.valueOf(version));
		try {
			Files.createDirectories(dest);
			copyTree(skillDir(id), dest);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to publish version", ex);
		}
		return toVersionView(id, version);
	}

	public void restoreVersion(String id, int version) {
		Path source = versionDir(id, version);
		Path dest = skillDir(id);
		try (Stream<Path> children = Files.list(dest)) {
			for (Path child : children.toList()) {
				if (".versions".equals(child.getFileName().toString())) {
					continue;
				}
				deleteRecursively(child);
			}
			copyTree(source, dest);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to restore version " + version, ex);
		}
	}

	public String catalogPrompt(List<String> skillIds) {
		if (skillIds == null || skillIds.isEmpty()) {
			return "";
		}
		StringBuilder prompt = new StringBuilder();
		prompt.append("## 已绑定技能\n");
		prompt.append("下面只列出名称和适用场景，不要把未加载的说明书当已读。\n");
		prompt.append("匹配到某条技能时：先 load_skill 读取 SKILL.md；需要参考文件时再 read_skill_resource；");
		prompt.append("scripts/ 下的文件用 run_skill_script 执行，不要整份读进上下文。\n");
		boolean any = false;
		for (String skillId : skillIds) {
			SkillView skill;
			try {
				skill = require(skillId);
			}
			catch (RuntimeException ex) {
				continue;
			}
			any = true;
			prompt.append("\n### ").append(skill.name()).append(" (`").append(skill.id()).append("`)\n");
			if (skill.description() != null && !skill.description().isBlank()) {
				prompt.append(skill.description().trim()).append("\n");
			}
		}
		return any ? prompt.toString().trim() : "";
	}

	public String loadForTool(String id) {
		SkillView skill = require(id);
		List<String> files = listFiles(id);
		StringBuilder out = new StringBuilder();
		out.append("# ").append(skill.name()).append(" (`").append(skill.id()).append("`)\n\n");
		if (skill.description() != null && !skill.description().isBlank()) {
			out.append(skill.description().trim()).append("\n\n");
		}
		out.append(skill.body() == null ? "" : skill.body().trim());
		out.append("\n\n## 可按需加载的文件\n");
		if (files.isEmpty()) {
			out.append("（无额外文件）\n");
		}
		else {
			for (String path : files) {
				if ("SKILL.md".equals(path)) {
					continue;
				}
				if (SkillPaths.isScript(path)) {
					out.append("- `").append(path).append("` — 用 run_skill_script 执行，不要当文本读\n");
				}
				else {
					out.append("- `").append(path).append("` — 用 read_skill_resource 读取\n");
				}
			}
		}
		return out.toString().trim();
	}

	public String runScript(String id, String script, List<String> args) {
		require(id);
		String relative = SkillPaths.scriptFileName(script);
		Path file = SkillPaths.resolve(skillDir(id), relative);
		if (!Files.isRegularFile(file)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Script not found");
		}
		List<String> command = new ArrayList<>();
		command.addAll(interpreterFor(file));
		command.add(file.toAbsolutePath().toString());
		if (args != null) {
			int count = 0;
			for (String arg : args) {
				if (arg == null) {
					continue;
				}
				if (count++ >= 20) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many script arguments");
				}
				if (arg.length() > 4000) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Script argument is too long");
				}
				command.add(arg);
			}
		}
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(skillDir(id).toFile());
		builder.redirectErrorStream(true);
		builder.environment().put("SKILL_DIR", skillDir(id).toString());
		try {
			Process process = builder.start();
			boolean finished = process.waitFor(scriptTimeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!finished) {
				process.destroyForcibly();
				return "ERROR: script timed out after " + scriptTimeout.toMillis() + "ms";
			}
			byte[] output = process.getInputStream().readAllBytes();
			String text = new String(output, 0, Math.min(output.length, MAX_SCRIPT_OUTPUT), StandardCharsets.UTF_8);
			if (output.length > MAX_SCRIPT_OUTPUT) {
				text += "\n...[truncated]";
			}
			if (process.exitValue() != 0) {
				return "ERROR: exit " + process.exitValue() + "\n" + text;
			}
			return text.isBlank() ? "(no output)" : text;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return "ERROR: script interrupted";
		}
		catch (IOException ex) {
			return "ERROR: failed to start script: " + ex.getMessage();
		}
	}

	private List<String> interpreterFor(Path script) {
		String name = script.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".js") || name.endsWith(".mjs") || name.endsWith(".cjs")) {
			return List.of("node");
		}
		if (name.endsWith(".py")) {
			return List.of(pythonCommand());
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported script type; use .js or .py");
	}

	private static String pythonCommand() {
		for (String candidate : List.of("python", "py", "python3")) {
			try {
				Process process = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
				if (process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0) {
					return candidate;
				}
			}
			catch (Exception ignored) {
				// try next
			}
		}
		return "python";
	}

	private SkillView write(String id, SkillView request) {
		try {
			Files.createDirectories(skillDir(id));
			SkillView stored = new SkillView(
					id,
					blankTo(request.name(), id),
					nullToEmpty(request.description()),
					nullToEmpty(request.body()));
			Files.writeString(skillFile(id), SkillMarkdown.write(stored));
			return stored.withLatestVersion(latestVersionNumber(id));
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to write skill " + id, ex);
		}
	}

	private SkillView readOrSkip(String id) {
		try {
			return require(id);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private List<String> listEntries(String id, Integer version, boolean filesOnly) {
		Path root = contentRoot(id, version);
		try (Stream<Path> walk = Files.walk(root)) {
			return walk
					.filter(path -> !path.equals(root))
					.filter(path -> !isProtected(root, path))
					.filter(path -> !filesOnly || Files.isRegularFile(path))
					.map(path -> {
						String relative = SkillPaths.posixRelative(root, path);
						return Files.isDirectory(path) ? relative + "/" : relative;
					})
					.sorted(Comparator.naturalOrder())
					.toList();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to list skill files " + id, ex);
		}
	}

	private Path contentRoot(String id, Integer version) {
		if (version == null) {
			if (!Files.exists(skillFile(id))) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
			}
			return skillDir(id);
		}
		return versionDir(id, version);
	}

	private Path versionsRoot(String id) {
		return skillDir(id).resolve(".versions");
	}

	private Path versionDir(String id, int version) {
		if (version < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version");
		}
		Path dir = versionsRoot(id).resolve(String.valueOf(version)).normalize();
		if (!dir.startsWith(versionsRoot(id)) || !Files.isDirectory(dir)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found");
		}
		return dir;
	}

	private int nextVersion(String id) {
		return latestVersionNumber(id) + 1;
	}

	private int latestVersionNumber(String id) {
		Path versions = versionsRoot(id);
		if (!Files.isDirectory(versions)) {
			return 0;
		}
		try (Stream<Path> stream = Files.list(versions)) {
			return stream
					.filter(Files::isDirectory)
					.map(Path::getFileName)
					.map(Path::toString)
					.filter(name -> name.matches("[0-9]+"))
					.mapToInt(Integer::parseInt)
					.max()
					.orElse(0);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to list versions", ex);
		}
	}

	private SkillVersionView toVersionView(String id, int version) {
		Path dir = versionDir(id, version);
		try {
			Instant instant = Files.getLastModifiedTime(dir).toInstant();
			String createdAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME
					.withZone(ZoneId.systemDefault())
					.format(instant);
			return new SkillVersionView(version, createdAt);
		}
		catch (IOException ex) {
			return new SkillVersionView(version, "");
		}
	}

	private void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path source : walk.toList()) {
				if (isProtected(from, source)) {
					continue;
				}
				Path dest = to.resolve(from.relativize(source)).normalize();
				if (!dest.startsWith(to)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid skill file path");
				}
				if (Files.isDirectory(source)) {
					Files.createDirectories(dest);
				}
				else {
					Files.createDirectories(dest.getParent());
					Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private void deleteRecursively(Path target) {
		try (Stream<Path> walk = Files.walk(target)) {
			walk.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ex) {
					throw new IllegalStateException("Failed to delete " + path, ex);
				}
			});
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to delete " + target, ex);
		}
	}

	private static boolean isProtected(Path root, Path path) {
		if (path.equals(root)) {
			return false;
		}
		return SkillPaths.isProtected(SkillPaths.posixRelative(root, path));
	}

	private Path skillDir(String id) {
		String safe = normalizeId(id);
		Path dir = skillsDir.resolve(safe).normalize();
		if (!dir.startsWith(skillsDir)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid skill id");
		}
		return dir;
	}

	private Path skillFile(String id) {
		return skillDir(id).resolve("SKILL.md");
	}

	static String normalizeId(String id) {
		if (id == null || id.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill id is required");
		}
		String normalized = id.trim().toLowerCase(Locale.ROOT);
		if (!normalized.matches("[a-z0-9-]+")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill id must be lowercase letters, digits or hyphen");
		}
		return normalized;
	}

	private static String blankTo(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
