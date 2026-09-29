package com.agentplatform.hub.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class SkillServiceTest {

	@TempDir
	Path dir;

	SkillService skills;

	@BeforeEach
	void setUp() throws Exception {
		skills = new SkillService(dir.toString(), mock(AgentSkillRepository.class), 8000);
	}

	@Test
	void catalogPromptDoesNotIncludeBody() {
		skills.create(new SkillView("customer-reply", "客服回复", "投诉时使用", "先安抚，再给处理时限。"));
		String prompt = skills.catalogPrompt(List.of("customer-reply"));
		assertTrue(prompt.contains("已绑定技能"));
		assertTrue(prompt.contains("客服回复"));
		assertTrue(prompt.contains("load_skill"));
		assertFalse(prompt.contains("先安抚，再给处理时限。"));
	}

	@Test
	void loadForToolListsExtraFiles() {
		skills.create(new SkillView("customer-reply", "客服回复", "投诉时使用", "先读参考文件。"));
		skills.writeFile("customer-reply", "references/input.md", "提取诉求和时限。");
		String loaded = skills.loadForTool("customer-reply");
		assertTrue(loaded.contains("先读参考文件。"));
		assertTrue(loaded.contains("references/input.md"));
	}

	@Test
	void rejectsPathTraversal() {
		skills.create(new SkillView("customer-reply", "客服回复", "投诉时使用", "正文"));
		assertThrows(ResponseStatusException.class, () -> skills.readFile("customer-reply", "../secret.md"));
	}

	@Test
	void publishesAndRestoresVersion() {
		skills.create(new SkillView("customer-reply", "客服回复", "投诉时使用", "v1正文"));
		SkillVersionView published = skills.publishVersion("customer-reply");
		assertEquals(1, published.version());
		skills.update("customer-reply", new SkillView("customer-reply", "客服回复", "投诉时使用", "v2正文"));
		skills.restoreVersion("customer-reply", 1);
		assertEquals("v1正文", skills.require("customer-reply").body());
		skills.update("customer-reply", new SkillView("customer-reply", "客服回复", "投诉时使用", "v3草稿"));
		SkillVersionView second = skills.publishVersion("customer-reply");
		assertEquals(2, second.version());
		assertEquals(2, skills.require("customer-reply").latestVersion());
	}

	@Test
	void renameAndHideVersionsFromTree() {
		skills.create(new SkillView("customer-reply", "客服回复", "投诉时使用", "正文"));
		skills.writeFile("customer-reply", "references/input.md", "入参");
		skills.renameFile("customer-reply", "references/input.md", "references/payload.md");
		skills.publishVersion("customer-reply");
		List<String> tree = skills.listTree("customer-reply", null);
		assertTrue(tree.contains("references/payload.md"));
		assertFalse(tree.stream().anyMatch(path -> path.startsWith(".versions")));
	}

}
