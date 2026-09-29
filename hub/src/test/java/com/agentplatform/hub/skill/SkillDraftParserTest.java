package com.agentplatform.hub.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkillDraftParserTest {

	@Test
	void parsesFencedJsonAndDropsInvalidPaths() {
		String raw = """
				```json
				{
				  "name": "客服回复",
				  "code": "Customer-Reply",
				  "description": "投诉和催单时使用",
				  "body": "先读 references/input.md",
				  "files": [
				    {"path": "references/input.md", "content": "提取诉求"},
				    {"path": "scripts/lib/check.js", "content": "nope"},
				    {"path": "参考/note.md", "content": "nope"}
				  ]
				}
				```
				""";

		SkillDraftParser.Draft draft = SkillDraftParser.parse(raw);

		assertEquals("客服回复", draft.name());
		assertEquals("customer-reply", draft.code());
		assertEquals("投诉和催单时使用", draft.description());
		assertEquals("先读 references/input.md", draft.body());
		assertEquals(1, draft.files().size());
		assertEquals("references/input.md", draft.files().get(0).path());
		assertEquals(2, draft.skipped().size());
		assertTrue(draft.skipped().contains("scripts/lib/check.js"));
		assertTrue(draft.skipped().contains("参考/note.md"));
	}

}
