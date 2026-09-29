package com.agentplatform.hub.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.agentplatform.hub.knowledge.KnowledgeService.Hit;
import com.agentplatform.hub.knowledge.KnowledgeService.Retrieval;

class KnowledgePromptTest {

	@Test
	void unboundKnowledgeLeavesPromptUntouched() {
		StringBuilder system = new StringBuilder("先查询知识库");
		KnowledgePrompt.append(system, Retrieval.empty());
		assertEquals("先查询知识库", system.toString());
	}

	@Test
	void missTellsModelToContinueLaterSteps() {
		StringBuilder system = new StringBuilder("先查询知识库，未命中再生成号码");
		KnowledgePrompt.append(system, Retrieval.miss());
		assertTrue(system.toString().contains("没有命中相关资料"));
		assertTrue(system.toString().contains("继续执行用户提示词中的后续步骤"));
		assertFalse(system.toString().contains("只输出资料中的原文"));
	}

	@Test
	void hitStopsAfterOriginalText() {
		Retrieval retrieval = new Retrieval(
				"检索到的知识库资料：\n\n【产品库 / 说明.txt】\n答：我能生成彩票信息",
				List.of(new Hit("产品库", "说明.txt", "答：我能生成彩票信息")),
				true);
		StringBuilder system = new StringBuilder("先查询知识库，命中后不要生成号码");
		KnowledgePrompt.append(system, retrieval);
		assertTrue(system.toString().contains("答：我能生成彩票信息"));
		assertTrue(system.toString().contains("只输出资料中的原文"));
		assertTrue(system.toString().contains("不要执行后续步骤"));
		assertTrue(system.toString().contains("如果资料和用户问题不对应"));
	}

}
