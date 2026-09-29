package com.agentplatform.hub.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatProxyExtractDeltaTest {

	@Test
	void readsStreamDelta() {
		String json = "{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}";
		assertEquals("你好", ChatProxyService.extractDelta(json));
	}

	@Test
	void skipsNullContent() {
		assertEquals("", ChatProxyService.extractDelta("{\"choices\":[{\"delta\":{\"content\":null}}]}"));
	}

	@Test
	void wrapsDeltaPayload() {
		assertEquals("你好", ChatProxyService.extractDelta(ChatProxyService.deltaPayload("你好")));
	}

}
