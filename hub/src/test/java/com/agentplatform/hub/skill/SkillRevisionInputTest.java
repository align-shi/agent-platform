package com.agentplatform.hub.skill;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SkillRevisionInputTest {

	@Test
	void includesCurrentSkillAndChangeRequest() {
		String text = SkillGenerateService.revisionInput(
				"客服回复",
				"customer-reply",
				"投诉时使用",
				"先确认诉求",
				List.of(new SkillGenerateService.GeneratedFile("references/tone.md", "语气平和")),
				"补上必须确认工单号");

		assertTrue(text.contains("编码：customer-reply"));
		assertTrue(text.contains("先确认诉求"));
		assertTrue(text.contains("文件 references/tone.md："));
		assertTrue(text.contains("语气平和"));
		assertTrue(text.contains("修改说明：\n补上必须确认工单号"));
	}

}
