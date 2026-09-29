package com.agentplatform.hub.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkillMarkdownTest {

	@Test
	void parsesFrontMatter() {
		SkillView skill = SkillMarkdown.parse("customer-reply", """
				---
				name: 客服回复
				description: 用户投诉或催单时使用
				---

				先安抚，再给处理时限。
				""");
		assertEquals("customer-reply", skill.id());
		assertEquals("客服回复", skill.name());
		assertEquals("用户投诉或催单时使用", skill.description());
		assertEquals("先安抚，再给处理时限。", skill.body());
	}

	@Test
	void writeRoundTrip() {
		SkillView original = new SkillView("refund", "退款说明", "用户要求退款时使用", "先核对订单，再给时限。");
		SkillView parsed = SkillMarkdown.parse("refund", SkillMarkdown.write(original));
		assertEquals(original, parsed);
	}

}
