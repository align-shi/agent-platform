package com.agentplatform.hub.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class FeishuBotServiceTest {

	@Test
	void normalizesPublicBaseToOrigin() {
		assertNull(FeishuBotService.normalizePublicBase("  "));
		assertEquals("https://bot.example.com", FeishuBotService.normalizePublicBase("https://bot.example.com/"));
		assertEquals(
				"https://bot.example.com",
				FeishuBotService.normalizePublicBase("https://bot.example.com/api/feishu/events/bot-1"));
	}

	@Test
	void rejectsNonHttpsPublicBase() {
		assertThrows(ResponseStatusException.class, () -> FeishuBotService.normalizePublicBase("http://bot.example.com"));
	}

}
