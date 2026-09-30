package com.agentplatform.hub.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SkillService {

	private static final int WORKING = 0;
	private static final int MAX_FILE_BYTES = 256 * 1024;
	private static final int MAX_SCRIPT_OUTPUT = 32 * 1024;
	private static final String SKILL_FILE = "SKILL.md";

	private final SkillFileRepository files;
	private final SkillVersionRepository versions;
	private final AgentSkillRepository agentSkills;
	private final Duration scriptTimeout;

	public SkillService(
			SkillFileRepository files,
			SkillVersionRepository versions,
			AgentSkillRepository agentSkills,
			@Value("${agent-platform.skill-script-timeout-ms:15000}") long scriptTimeoutMs) {
		this.files = files;
		this.versions = versions;
		this.agentSkills = agentSkills;
		this.scriptTimeout = Duration.ofMillis(scriptTimeoutMs);
	}

	public List<SkillView> list() {
		return files.findWorkingCopies().stream()
				.map(SkillFileEntity::getSkillId)
				.sorted(Comparator.naturalOrder())
				.map(this::readOrSkip)
				.filter(item -> item != null)
				.toList();
	}

	public SkillView require(String id) {
		String skillId = normalizeId(id);
		SkillFileEntity file = files.findBySkillIdAndVersionAndPath(skillId, WORKING, SKILL_FILE)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));
		return SkillMarkdown.parse(skillId, file.getContent()).withLatestVersion(latestVersionNumber(skillId));
	}

	@Transactional
	public SkillView create(SkillView request) {
		String id = normalizeId(request.id());
		if (files.existsBySkillIdAndVersionAndPath(id, WORKING, SKILL_FILE)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Skill already exists");
		}
		return writeMarkdown(id, request);
	}

	@Transactional
	public SkillView update(String id, SkillView request) {
		require(id);
		return writeMarkdown(normalizeId(id), request);
	}

	@Transactional(readOnly = true)
	public byte[] exportZip(String id, Integer version) {
		String skillId = normalizeId(id);
		int storedVersion = resolveVersion(id, version);
		List<SkillZip.Entry> entries = files.findBySkillIdAndVersionOrderByPathAsc(skillId, storedVersion).stream()
				.map(row -> new SkillZip.Entry(row.getPath(), row.isDirectory(), row.getContent()))
				.toList();
		return SkillZip.write(skillId, entries);
	}

	@Transactional
	public ImportResult importZip(String filename, byte[] bytes) {
		SkillZip.Archive archive = SkillZip.read(bytes, filename);
		boolean created = !files.existsBySkillIdAndVersionAndPath(archive.id(), WORKING, SKILL_FILE);
		if (!created) {
			files.deleteBySkillIdAndVersion(archive.id(), WORKING);
			files.flush();
		}
		for (SkillZip.Entry entry : archive.entries()) {
			if (entry.directory()) {
				ensureDirectory(archive.id(), WORKING, entry.path());
			}
		}
		List<SkillFileEntity> rows = new ArrayList<>();
		for (SkillZip.Entry entry : archive.entries()) {
			if (entry.directory()) {
				continue;
			}
			ensureParents(archive.id(), WORKING, entry.path());
			rows.add(newRow(archive.id(), WORKING, entry.path(), false, entry.content()));
		}
		files.saveAll(rows);
		SkillView skill = require(archive.id());
		return new ImportResult(skill.id(), skill.name(), created);
	}

	@Transactional
	public void delete(String id) {
		String skillId = normalizeId(id);
		if (!files.existsBySkillIdAndVersionAndPath(skillId, WORKING, SKILL_FILE)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
		}
		agentSkills.deleteBySkillId(skillId);
		files.deleteBySkillId(skillId);
		versions.deleteBySkillId(skillId);
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
		String path = SkillPaths.normalizeRelative(relativePath);
		int storedVersion = resolveVersion(id, version);
		SkillFileEntity file = files.findBySkillIdAndVersionAndPath(normalizeId(id), storedVersion, path)
				.filter(row -> !row.isDirectory())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill file not found"));
		String content = file.getContent() == null ? "" : file.getContent();
		if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill file is too large");
		}
		return new SkillFileView(path, content);
	}

	@Transactional
	public SkillFileView writeFile(String id, String relativePath, String content) {
		String skillId = normalizeId(id);
		require(skillId);
		String path = SkillPaths.normalizeRelative(relativePath);
		SkillPaths.assertFile(path);
		String text = content == null ? "" : content;
		if (text.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill file is too large");
		}
		ensureParents(skillId, WORKING, path);
		SkillFileEntity row = files.findBySkillIdAndVersionAndPath(skillId, WORKING, path).orElse(null);
		if (row != null && row.isDirectory()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Target already exists");
		}
		if (row == null) {
			row = newRow(skillId, WORKING, path, false, text);
		}
		else {
			row.setContent(text);
			row.setUpdatedAt(Instant.now());
		}
		files.save(row);
		return new SkillFileView(path, text);
	}

	@Transactional
	public void deleteFile(String id, String relativePath) {
		String skillId = normalizeId(id);
		String path = SkillPaths.normalizeRelative(relativePath);
		if (SKILL_FILE.equals(path)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete SKILL.md; delete the skill instead");
		}
		List<SkillFileEntity> matched = files.findBySkillIdAndVersionOrderByPathAsc(skillId, WORKING).stream()
				.filter(row -> matchesTree(row.getPath(), path))
				.toList();
		if (matched.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill file not found");
		}
		files.deleteAll(matched);
	}

	@Transactional
	public SkillFileView renameFile(String id, String from, String to) {
		String skillId = normalizeId(id);
		require(skillId);
		String sourcePath = SkillPaths.normalizeRelative(from);
		String destPath = SkillPaths.normalizeRelative(to);
		if (SKILL_FILE.equals(sourcePath) || SKILL_FILE.equals(destPath)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot rename SKILL.md");
		}
		if (destPath.equals(sourcePath) || destPath.startsWith(sourcePath + "/")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid skill file path");
		}
		List<SkillFileEntity> rows = files.findBySkillIdAndVersionOrderByPathAsc(skillId, WORKING);
		List<SkillFileEntity> matched = rows.stream().filter(row -> matchesTree(row.getPath(), sourcePath)).toList();
		if (matched.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill file not found");
		}
		boolean destExists = rows.stream().anyMatch(row -> matchesTree(row.getPath(), destPath));
		if (destExists) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Target already exists");
		}
		boolean directory = matched.stream().anyMatch(row -> row.getPath().equals(sourcePath) && row.isDirectory());
		ensureParents(skillId, WORKING, destPath);
		for (SkillFileEntity row : matched) {
			String suffix = row.getPath().equals(sourcePath) ? "" : row.getPath().substring(sourcePath.length());
			row.setPath(destPath + suffix);
			row.setUpdatedAt(Instant.now());
		}
		files.saveAll(matched);
		if (directory) {
			return new SkillFileView(destPath, "");
		}
		return readFile(skillId, destPath);
	}

	@Transactional
	public void createDirectory(String id, String relativePath) {
		String skillId = normalizeId(id);
		require(skillId);
		String path = SkillPaths.normalizeRelative(relativePath);
		SkillPaths.assertDirectory(path);
		ensureDirectory(skillId, WORKING, path);
	}

	public List<SkillVersionView> listVersions(String id) {
		String skillId = normalizeId(id);
		require(skillId);
		return versions.findBySkillIdOrderByVersionDesc(skillId).stream()
				.map(this::toVersionView)
				.toList();
	}

	@Transactional
	public SkillVersionView publishVersion(String id) {
		String skillId = normalizeId(id);
		require(skillId);
		int version = latestVersionNumber(skillId) + 1;
		List<SkillFileEntity> copies = files.findBySkillIdAndVersionOrderByPathAsc(skillId, WORKING).stream()
				.map(row -> copyRow(row, version))
				.toList();
		files.saveAll(copies);
		SkillVersionEntity published = newVersion(skillId, version, Instant.now());
		versions.save(published);
		return toVersionView(published);
	}

	@Transactional
	public void restoreVersion(String id, int version) {
		String skillId = normalizeId(id);
		require(skillId);
		if (versions.findBySkillIdAndVersion(skillId, version).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found");
		}
		List<SkillFileEntity> snapshot = files.findBySkillIdAndVersionOrderByPathAsc(skillId, version);
		List<SkillFileEntity> current = files.findBySkillIdAndVersionOrderByPathAsc(skillId, WORKING);
		files.deleteAll(current);
		files.flush();
		files.saveAll(snapshot.stream().map(row -> copyRow(row, WORKING)).toList());
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
		List<String> entries = listFiles(id);
		StringBuilder out = new StringBuilder();
		out.append("# ").append(skill.name()).append(" (`").append(skill.id()).append("`)\n\n");
		if (skill.description() != null && !skill.description().isBlank()) {
			out.append(skill.description().trim()).append("\n\n");
		}
		out.append(skill.body() == null ? "" : skill.body().trim());
		out.append("\n\n## 可按需加载的文件\n");
		if (entries.isEmpty()) {
			out.append("（无额外文件）\n");
		}
		else {
			for (String path : entries) {
				if (SKILL_FILE.equals(path)) {
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
		String skillId = normalizeId(id);
		require(skillId);
		String relative = SkillPaths.scriptFileName(script);
		List<SkillFileEntity> rows = files.findBySkillIdAndVersionOrderByPathAsc(skillId, WORKING);
		boolean present = rows.stream().anyMatch(row -> relative.equals(row.getPath()) && !row.isDirectory());
		if (!present) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Script not found");
		}
		Path temp;
		try {
			temp = Files.createTempDirectory("agent-skill-");
			materialize(temp, rows);
		}
		catch (IOException ex) {
			return "ERROR: failed to start script: " + ex.getMessage();
		}
		try {
			return executeScript(temp.resolve(relative), temp, args);
		}
		finally {
			deleteRecursively(temp);
		}
	}

	@Transactional
	public boolean importSkillDirectory(Path skillDir) throws IOException {
		if (skillDir == null || !Files.isDirectory(skillDir)) {
			return false;
		}
		String id;
		try {
			id = normalizeId(skillDir.getFileName().toString());
		}
		catch (ResponseStatusException ex) {
			return false;
		}
		if (!Files.isRegularFile(skillDir.resolve(SKILL_FILE))) {
			return false;
		}
		if (files.existsBySkillIdAndVersionAndPath(id, WORKING, SKILL_FILE)) {
			return false;
		}
		importTree(id, WORKING, skillDir);
		Path versionRoot = skillDir.resolve(".versions");
		if (Files.isDirectory(versionRoot)) {
			try (Stream<Path> stream = Files.list(versionRoot)) {
				for (Path child : stream.filter(Files::isDirectory).toList()) {
					String name = child.getFileName().toString();
					if (!name.matches("[0-9]+")) {
						continue;
					}
					int version = Integer.parseInt(name);
					if (version < 1) {
						continue;
					}
					importTree(id, version, child);
					Instant createdAt = Files.getLastModifiedTime(child).toInstant();
					versions.save(newVersion(id, version, createdAt));
				}
			}
		}
		return true;
	}

	private void importTree(String skillId, int version, Path root) throws IOException {
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path source : walk.toList()) {
				if (source.equals(root)) {
					continue;
				}
				String relative = root.relativize(source).toString().replace('\\', '/');
				if (SkillPaths.isProtected(relative)) {
					continue;
				}
				String path;
				try {
					path = SkillPaths.normalizeRelative(relative);
				}
				catch (ResponseStatusException ex) {
					continue;
				}
				if (Files.isDirectory(source)) {
					if (files.existsBySkillIdAndVersionAndPath(skillId, version, path)) {
						continue;
					}
					files.save(newRow(skillId, version, path, true, ""));
					continue;
				}
				if (!Files.isRegularFile(source)) {
					continue;
				}
				String content = Files.readString(source);
				if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
					throw new IllegalStateException("Skill file is too large: " + path);
				}
				files.save(newRow(skillId, version, path, false, content));
			}
		}
	}

	private String executeScript(Path script, Path workDir, List<String> args) {
		List<String> command = new ArrayList<>();
		command.addAll(interpreterFor(script));
		command.add(script.toAbsolutePath().toString());
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
		builder.directory(workDir.toFile());
		builder.redirectErrorStream(true);
		builder.environment().put("SKILL_DIR", workDir.toString());
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

	private void materialize(Path root, List<SkillFileEntity> rows) throws IOException {
		for (SkillFileEntity row : rows) {
			if (!row.isDirectory()) {
				continue;
			}
			Files.createDirectories(root.resolve(row.getPath()));
		}
		for (SkillFileEntity row : rows) {
			if (row.isDirectory()) {
				continue;
			}
			Path file = root.resolve(row.getPath());
			Files.createDirectories(file.getParent());
			Files.writeString(file, row.getContent() == null ? "" : row.getContent());
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

	private SkillView writeMarkdown(String id, SkillView request) {
		SkillView stored = new SkillView(
				id,
				blankTo(request.name(), id),
				nullToEmpty(request.description()),
				nullToEmpty(request.body()));
		String text = SkillMarkdown.write(stored);
		SkillFileEntity row = files.findBySkillIdAndVersionAndPath(id, WORKING, SKILL_FILE).orElse(null);
		if (row == null) {
			row = newRow(id, WORKING, SKILL_FILE, false, text);
		}
		else {
			row.setContent(text);
			row.setDirectory(false);
			row.setUpdatedAt(Instant.now());
		}
		files.save(row);
		return stored.withLatestVersion(latestVersionNumber(id));
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
		int storedVersion = resolveVersion(id, version);
		return files.findBySkillIdAndVersionOrderByPathAsc(normalizeId(id), storedVersion).stream()
				.map(row -> row.isDirectory() ? row.getPath() + "/" : row.getPath())
				.filter(path -> !filesOnly || !path.endsWith("/"))
				.sorted(Comparator.naturalOrder())
				.toList();
	}

	private int resolveVersion(String id, Integer version) {
		String skillId = normalizeId(id);
		if (version == null) {
			if (!files.existsBySkillIdAndVersionAndPath(skillId, WORKING, SKILL_FILE)) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
			}
			return WORKING;
		}
		if (version < 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version");
		}
		if (versions.findBySkillIdAndVersion(skillId, version).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found");
		}
		return version;
	}

	private int latestVersionNumber(String skillId) {
		return versions.findBySkillIdOrderByVersionDesc(skillId).stream()
				.mapToInt(SkillVersionEntity::getVersion)
				.max()
				.orElse(0);
	}

	private void ensureParents(String skillId, int version, String path) {
		int slash = path.lastIndexOf('/');
		if (slash <= 0) {
			return;
		}
		ensureDirectory(skillId, version, path.substring(0, slash));
	}

	private void ensureDirectory(String skillId, int version, String path) {
		String[] parts = path.split("/");
		StringBuilder current = new StringBuilder();
		for (String part : parts) {
			if (!current.isEmpty()) {
				current.append('/');
			}
			current.append(part);
			String dir = current.toString();
			SkillFileEntity existing = files.findBySkillIdAndVersionAndPath(skillId, version, dir).orElse(null);
			if (existing != null) {
				if (!existing.isDirectory()) {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "Target already exists");
				}
				continue;
			}
			files.save(newRow(skillId, version, dir, true, ""));
		}
	}

	private SkillVersionView toVersionView(SkillVersionEntity version) {
		String createdAt = version.getCreatedAt() == null
				? ""
				: DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault()).format(version.getCreatedAt());
		return new SkillVersionView(version.getVersion(), createdAt);
	}

	private SkillFileEntity newRow(String skillId, int version, String path, boolean directory, String content) {
		SkillFileEntity row = new SkillFileEntity();
		row.setId(UUID.randomUUID().toString());
		row.setSkillId(skillId);
		row.setVersion(version);
		row.setPath(path);
		row.setDirectory(directory);
		row.setContent(directory ? "" : content);
		row.setUpdatedAt(Instant.now());
		return row;
	}

	private SkillFileEntity copyRow(SkillFileEntity source, int version) {
		return newRow(
				source.getSkillId(),
				version,
				source.getPath(),
				source.isDirectory(),
				source.getContent() == null ? "" : source.getContent());
	}

	private SkillVersionEntity newVersion(String skillId, int version, Instant createdAt) {
		SkillVersionEntity row = new SkillVersionEntity();
		row.setId(UUID.randomUUID().toString());
		row.setSkillId(skillId);
		row.setVersion(version);
		row.setCreatedAt(createdAt);
		return row;
	}

	private void deleteRecursively(Path target) {
		if (!Files.exists(target)) {
			return;
		}
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

	private static boolean matchesTree(String path, String root) {
		return path.equals(root) || path.startsWith(root + "/");
	}

	public record ImportResult(String id, String name, boolean created) {
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
