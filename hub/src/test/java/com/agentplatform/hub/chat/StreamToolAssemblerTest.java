package com.agentplatform.hub.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StreamToolAssemblerTest {

	@Test
	void streamsContentBeforeToolCallFragments() {
		StreamToolAssembler assembler = new StreamToolAssembler();
		assertEquals("我来查一下。", assembler.accept(
				"{\"choices\":[{\"delta\":{\"content\":\"我来查一下。\"}}]}"));
		assertEquals("", assembler.accept(
				"{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_lottery\",\"arguments\":\"\"}}]}}]}"));
		assertEquals("", assembler.accept(
				"{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"function\":{\"arguments\":\"{\\\"game\\\":\\\"ssq\\\"}\"}}]}}]}"));

		ChatRound round = assembler.toRound();
		assertEquals("我来查一下。", round.content());
		assertTrue(round.hasToolCalls());
		assertEquals("get_lottery", round.toolCalls().get(0).name());
		assertEquals("{\"game\":\"ssq\"}", round.toolCalls().get(0).arguments());
		assertEquals("call_1", round.toolCalls().get(0).id());
		assertTrue(round.usage().isEmpty());
	}

	@Test
	void keepsUsageFromTheFinalChunk() {
		StreamToolAssembler assembler = new StreamToolAssembler();
		assembler.accept("{\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}");
		assertEquals("", assembler.accept(
				"{\"choices\":[],\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":2,\"total_tokens\":13}}"));

		ChatRound round = assembler.toRound();
		assertEquals("你好", round.content());
		assertEquals(11, round.usage().promptTokens());
		assertEquals(2, round.usage().completionTokens());
		assertEquals(13, round.usage().totalTokens());
	}

	@Test
	void keepsParallelToolCallsInIndexOrder() {
		StreamToolAssembler assembler = new StreamToolAssembler();
		assembler.accept(
				"{\"choices\":[{\"delta\":{\"content\":null,\"tool_calls\":[{\"index\":1,\"id\":\"b\",\"function\":{\"name\":\"second\",\"arguments\":\"{}\"}}]}}]}");
		assembler.accept(
				"{\"choices\":[{\"delta\":{\"tool_calls\":[{\"index\":0,\"id\":\"a\",\"function\":{\"name\":\"first\",\"arguments\":\"{}\"}}]}}]}");

		ChatRound round = assembler.toRound();
		assertEquals("", round.content());
		assertEquals(2, round.toolCalls().size());
		assertEquals("first", round.toolCalls().get(0).name());
		assertEquals("second", round.toolCalls().get(1).name());
		assertFalse(round.content().contains("null"));
	}

}
