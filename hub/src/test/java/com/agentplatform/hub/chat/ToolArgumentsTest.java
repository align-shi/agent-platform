package com.agentplatform.hub.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class ToolArgumentsTest {

	@Test
	void readsStringAndArrayFields() {
		String json = "{\"skill_id\":\"customer-reply\",\"path\":\"references/input.md\",\"args\":[\"a\",\"b\"]}";
		assertEquals("customer-reply", ToolArguments.string(json, "skill_id"));
		assertEquals("references/input.md", ToolArguments.string(json, "path"));
		assertEquals(List.of("a", "b"), ToolArguments.strings(json, "args"));
	}

}
