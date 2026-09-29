package com.agentplatform.hub.skill;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class SkillFolderImporter implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SkillFolderImporter.class);

	private final SkillService skills;
	private final String skillsDir;

	public SkillFolderImporter(SkillService skills, @Value("${agent-platform.skills-dir:}") String skillsDir) {
		this.skills = skills;
		this.skillsDir = skillsDir;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (skillsDir == null || skillsDir.isBlank()) {
			return;
		}
		Path root = Path.of(skillsDir);
		if (!Files.isDirectory(root)) {
			return;
		}
		try (var children = Files.list(root)) {
			for (Path child : children.filter(Files::isDirectory).sorted().toList()) {
				importOne(child);
			}
		}
		catch (Exception ex) {
			log.warn("failed to scan skills directory {}", root, ex);
		}
	}

	private void importOne(Path skillDir) {
		try {
			if (skills.importSkillDirectory(skillDir)) {
				log.info("imported skill {} from {}", skillDir.getFileName(), skillDir);
			}
		}
		catch (Exception ex) {
			log.warn("failed to import skill from {}", skillDir, ex);
		}
	}

}
