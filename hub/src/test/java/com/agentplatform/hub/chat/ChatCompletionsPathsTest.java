package com.agentplatform.hub.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatCompletionsPathsTest {

	@Test
	void deepseekHostGetsV1() {
		assertEquals(
				"https://api.deepseek.com/v1/chat/completions",
				ChatCompletionsPaths.resolve("https://api.deepseek.com"));
	}

	@Test
	void zhipuUsesV4NotV1() {
		assertEquals(
				"https://open.bigmodel.cn/api/paas/v4/chat/completions",
				ChatCompletionsPaths.resolve("https://open.bigmodel.cn/api/paas/v4/"));
	}

	@Test
	void moonshotAlreadyHasV1() {
		assertEquals(
				"https://api.moonshot.cn/v1/chat/completions",
				ChatCompletionsPaths.resolve("https://api.moonshot.cn/v1"));
	}

}
