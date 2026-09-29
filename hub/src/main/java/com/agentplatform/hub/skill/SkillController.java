package com.agentplatform.hub.skill;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/skills")
public class SkillController {

	private final SkillService skills;
	private final SkillGenerateService generator;

	public SkillController(SkillService skills, SkillGenerateService generator) {
		this.skills = skills;
		this.generator = generator;
	}

	@GetMapping
	public List<SkillView> list() {
		return skills.list();
	}

	@GetMapping("/{id}")
	public SkillView get(@PathVariable String id) {
		return skills.require(id);
	}

	@PostMapping("/generate")
	public SkillGenerateService.Generated generate(@Valid @RequestBody GenerateRequest request) {
		return generator.generate(request.brief(), request.skillId(), request.files());
	}

	@PostMapping
	public SkillView create(@Valid @RequestBody Upsert request) {
		return skills.create(new SkillView(request.id(), request.name(), request.description(), request.body()));
	}

	@PutMapping("/{id}")
	public SkillView update(@PathVariable String id, @Valid @RequestBody Upsert request) {
		return skills.update(id, new SkillView(id, request.name(), request.description(), request.body()));
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		skills.delete(id);
	}

	@GetMapping("/{id}/files")
	public List<String> files(@PathVariable String id, @RequestParam(required = false) Integer version) {
		return skills.listTree(id, version);
	}

	@GetMapping("/{id}/file")
	public SkillFileView readFile(
			@PathVariable String id,
			@RequestParam String path,
			@RequestParam(required = false) Integer version) {
		return skills.readFile(id, path, version);
	}

	@PutMapping("/{id}/file")
	public SkillFileView writeFile(@PathVariable String id, @Valid @RequestBody FileUpsert request) {
		return skills.writeFile(id, request.path(), request.content());
	}

	@DeleteMapping("/{id}/file")
	public void deleteFile(@PathVariable String id, @RequestParam String path) {
		skills.deleteFile(id, path);
	}

	@PostMapping("/{id}/dirs")
	public void createDirectory(@PathVariable String id, @Valid @RequestBody PathRequest request) {
		skills.createDirectory(id, request.path());
	}

	@PostMapping("/{id}/rename")
	public SkillFileView rename(@PathVariable String id, @Valid @RequestBody RenameRequest request) {
		return skills.renameFile(id, request.from(), request.to());
	}

	@GetMapping("/{id}/versions")
	public List<SkillVersionView> versions(@PathVariable String id) {
		return skills.listVersions(id);
	}

	@PostMapping("/{id}/versions")
	public SkillVersionView publish(@PathVariable String id) {
		return skills.publishVersion(id);
	}

	@PostMapping("/{id}/versions/{version}/restore")
	public void restore(@PathVariable String id, @PathVariable int version) {
		skills.restoreVersion(id, version);
	}

	public record GenerateRequest(
			@NotBlank String brief,
			String skillId,
			List<SkillGenerateService.CurrentFile> files) {
	}

	public record Upsert(
			String id,
			@NotBlank String name,
			String description,
			String body) {
	}

	public record FileUpsert(
			@NotBlank String path,
			String content) {
	}

	public record PathRequest(@NotBlank String path) {
	}

	public record RenameRequest(@NotBlank String from, @NotBlank String to) {
	}

}
