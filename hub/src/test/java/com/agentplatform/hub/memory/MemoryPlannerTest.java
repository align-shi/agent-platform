package com.agentplatform.hub.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.agentplatform.hub.agent.MemoryMode;
import com.agentplatform.hub.conversation.ConversationDtos;

class MemoryPlannerTest {

	@Test
	void noneModeKeepsOnlyTheLatestMessage() {
		List<ConversationDtos.MessageView> all = List.of(
				msg("u1", "user", "第一轮"),
				msg("a1", "assistant", "你好"),
				msg("u2", "user", "现在问什么"));
		List<ConversationDtos.MessageView> history = MemoryPlanner.historyForModel(MemoryMode.NONE, all, 0);
		assertEquals(1, history.size());
		assertEquals("现在问什么", history.get(0).content());
	}

	@Test
	void sessionModeSkipsSummarizedPrefix() {
		List<ConversationDtos.MessageView> all = List.of(
				msg("1", "user", "旧问题"),
				msg("2", "assistant", "旧回答"),
				msg("3", "user", "新问题"),
				msg("4", "assistant", "新回答"));
		List<ConversationDtos.MessageView> history = MemoryPlanner.historyForModel(MemoryMode.SESSION, all, 2);
		assertEquals(2, history.size());
		assertEquals("新问题", history.get(0).content());
		assertEquals("新回答", history.get(1).content());
	}

	@Test
	void compactTriggersWhenOverLimitAndMoreThanKeepLast() {
		assertTrue(MemoryPlanner.needsCompact(8000, 8000, 12, 10));
		assertFalse(MemoryPlanner.needsCompact(7999, 8000, 12, 10));
		assertFalse(MemoryPlanner.needsCompact(20000, 8000, 10, 10));
	}

	@Test
	void compactEndLeavesTheLatestWindow() {
		assertEquals(6, MemoryPlanner.compactEndIndex(0, 16, 10));
		assertEquals(4, MemoryPlanner.compactEndIndex(4, 12, 10));
	}

	@Test
	void composeSystemAddsMemorySections() {
		String system = MemoryService.composeSystem("你是助手", "用户在问发票", "偏好简洁回复");
		assertTrue(system.contains("你是助手"));
		assertTrue(system.contains("跨会话记忆"));
		assertTrue(system.contains("偏好简洁回复"));
		assertTrue(system.contains("本会话摘要"));
		assertTrue(system.contains("用户在问发票"));
	}

	private static ConversationDtos.MessageView msg(String id, String role, String content) {
		return new ConversationDtos.MessageView(id, role, content);
	}

}
