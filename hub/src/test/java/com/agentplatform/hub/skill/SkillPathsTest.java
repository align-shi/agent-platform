package com.agentplatform.hub.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SkillPathsTest {

	@Test
	void treatsScriptsAsExecutable() {
		assertTrue(SkillPaths.isScript("scripts/check_reply.js"));
		assertFalse(SkillPaths.isScript("references/input.md"));
		assertEquals("scripts/check_reply.js", SkillPaths.scriptFileName("check_reply.js"));
	}

	@Test
	void acceptsConventionFoldersAndRejectsNestedScripts() {
		SkillPaths.assertDirectory("references");
		SkillPaths.assertDirectory("templates/mail");
		SkillPaths.assertFile("scripts/check_reply.js");
		assertThrows(ResponseStatusException.class, () -> SkillPaths.assertDirectory("scripts/lib"));
		assertThrows(ResponseStatusException.class, () -> SkillPaths.assertDirectory("参考"));
		assertThrows(ResponseStatusException.class, () -> SkillPaths.assertDirectory(".hidden"));
	}

}
