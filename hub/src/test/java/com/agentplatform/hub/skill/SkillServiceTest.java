package com.agentplatform.hub.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class SkillServiceTest {

	@Autowired
	SkillService skills;

	@Autowired
	SkillFileRepository files;

	@Autowired
	SkillVersionRepository versions;

	@Autowired
	AgentSkillRepository agentSkills;

	@BeforeEach
	void clean() {
		agentSkills.deleteAll();
		files.deleteAll();
		versions.deleteAll();
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

	@Test
	void importsFolderIntoDatabase(@TempDir Path dir) throws Exception {
		Path skill = dir.resolve("customer-reply");
		Files.createDirectories(skill.resolve("references"));
		Files.writeString(skill.resolve("SKILL.md"), SkillMarkdown.write(
				new SkillView("customer-reply", "客服回复", "投诉时使用", "磁盘正文")));
		Files.writeString(skill.resolve("references/input.md"), "入参");
		Path published = skill.resolve(".versions/1");
		Files.createDirectories(published);
		Files.writeString(published.resolve("SKILL.md"), SkillMarkdown.write(
				new SkillView("customer-reply", "客服回复", "投诉时使用", "旧正文")));

		assertTrue(skills.importSkillDirectory(skill));
		assertFalse(skills.importSkillDirectory(skill));
		assertEquals("磁盘正文", skills.require("customer-reply").body());
		assertEquals(1, skills.require("customer-reply").latestVersion());
		assertEquals("入参", skills.readFile("customer-reply", "references/input.md").content());
		assertEquals("旧正文", SkillMarkdown.parse(
				"customer-reply",
				skills.readFile("customer-reply", "SKILL.md", 1).content()).body());
	}

	@Test
	void exportsZipAndImportsOverExistingSkill() {
		skills.create(new SkillView("customer-reply", "客服回复", "投诉时使用", "原文"));
		skills.writeFile("customer-reply", "references/input.md", "入参");
		skills.publishVersion("customer-reply");
		byte[] zip = skills.exportZip("customer-reply", null);

		byte[] fresh = SkillZip.write("order-check", List.of(
				new SkillZip.Entry("SKILL.md", false, SkillMarkdown.write(
						new SkillView("order-check", "查单", "查询订单", "按单号查"))),
				new SkillZip.Entry("references/input.md", false, "单号")));
		SkillService.ImportResult created = skills.importZip("order-check.zip", fresh);
		assertTrue(created.created());
		assertEquals("order-check", created.id());
		assertEquals("单号", skills.readFile("order-check", "references/input.md").content());

		skills.update("customer-reply", new SkillView("customer-reply", "客服回复", "投诉时使用", "已改"));
		SkillService.ImportResult overwritten = skills.importZip("customer-reply.zip", zip);
		assertFalse(overwritten.created());
		assertEquals("原文", skills.require("customer-reply").body());
		assertEquals("入参", skills.readFile("customer-reply", "references/input.md").content());
		assertEquals(1, skills.require("customer-reply").latestVersion());
	}

}
