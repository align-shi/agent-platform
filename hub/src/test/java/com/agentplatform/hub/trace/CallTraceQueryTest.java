package com.agentplatform.hub.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CallTraceQueryTest {

	@Autowired
	private CallTraceService traces;

	@Test
	void savesAndListsACall() {
		CallTraceRecorder recorder = CallTraceRecorder.start("agent-x", "结算助手", "provider-x", "demo-model", "conv-x", "查一下订单");
		recorder.systemPrompt("你是结算助手");
		recorder.beginRound();
		recorder.model("demo-model", "已查到", 3, 2, 5, 0, recorder.mark());
		traces.save(recorder.success());

		CallDtos.Summary summary = traces.list("agent-x", "conv-x").get(0);
		assertEquals("结算助手", summary.agentName());
		assertEquals("查一下订单", summary.userInput());
		assertEquals(5, summary.totalTokens());
		assertEquals(1, summary.modelCalls());

		CallDtos.Detail detail = traces.get(summary.id());
		assertFalse(detail.spans().isEmpty());
		assertEquals("SUCCESS", detail.call().status());
	}

}
