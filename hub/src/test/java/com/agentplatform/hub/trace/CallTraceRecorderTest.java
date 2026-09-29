package com.agentplatform.hub.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CallTraceRecorderTest {

	@Test
	void recordsOneRoundWithModelToolsAndTokens() {
		CallTraceRecorder trace = CallTraceRecorder.start("agent-1", "结算助手", "provider-1", "demo-model", "conv-1", "查一下订单");
		trace.systemPrompt("你是结算助手");
		trace.beginRound();
		long prep = trace.mark();
		trace.context("系统提示", "技能目录", "{\"name\":\"lookup\"}", "[用户]\n查一下订单", "", prep);
		long mark = trace.mark();
		trace.model("demo-model", "", 20, 8, 28, 1, mark);
		trace.tool("lookup", "mcp", "{\"input\":\"OA-1\"}", "已并单", trace.mark());
		trace.model("demo-model", "流程已并到采购订单。", 30, 12, 42, 0, trace.mark());
		trace.assistant("流程已并到采购订单。");

		CallTraceRecorder.Draft draft = trace.success();
		assertEquals("SUCCESS", draft.status());
		assertEquals(1, draft.rounds());
		assertEquals(2, draft.modelCalls());
		assertEquals(1, draft.toolCalls());
		assertEquals(1, draft.mcpCalls());
		assertEquals(50, draft.promptTokens());
		assertEquals(20, draft.completionTokens());
		assertEquals(70, draft.totalTokens());

		List<CallDtos.Span> spans = CallTraceJson.decode(draft.traceJson());
		assertEquals("system", spans.get(0).kind());
		CallDtos.Span round = spans.get(1);
		assertEquals("round", round.kind());
		assertEquals("模型 2 · 工具 1", round.summary());
		assertTrue(round.children().stream().anyMatch((span) -> "model".equals(span.kind())));
		assertTrue(round.children().stream().anyMatch((span) -> "tool".equals(span.kind())));
		assertTrue(round.children().stream().anyMatch((span) -> "context".equals(span.kind())));
		CallDtos.Span context = round.children().stream().filter((span) -> "context".equals(span.kind())).findFirst().orElseThrow();
		assertTrue(context.sections().stream().anyMatch((section) -> "组装提示词".equals(section.label())));
		assertTrue(draft.traceJson().contains("流程已并到采购订单。"));
	}

	@Test
	void keepsQuotesInTheTrace() {
		CallTraceRecorder trace = CallTraceRecorder.start("a", "助手", "p", "m", "c", "他说\"你好\"\n下一行");
		trace.systemPrompt("规则");
		trace.beginRound();
		CallTraceRecorder.Draft draft = trace.success();
		List<CallDtos.Span> spans = CallTraceJson.decode(draft.traceJson());
		CallDtos.Span round = spans.get(1);
		CallDtos.Span input = round.children().get(0);
		assertEquals("input", input.kind());
		assertEquals("他说\"你好\"\n下一行", input.sections().get(0).text());
	}

}
