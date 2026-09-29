package com.agentplatform.hub.chat;

import com.agentplatform.hub.knowledge.KnowledgeService.Retrieval;

final class KnowledgePrompt {

	static final String HIT = """
			本次已经完成知识库匹配，检索到了上面的资料。
			用户提示词里的「先查询知识库」这一步已经由平台完成，不要再查询。
			先判断这些资料是否直接回答了用户的问题。
			如果直接回答了：只输出资料中的原文，不要增删、改写、解释或补充，也不要附带资料以外的信息，不要执行后续步骤。
			如果资料和用户问题不对应：忽略这些资料，继续执行用户提示词中的后续步骤，不要把不相关的原文当作答案。
			""";

	static final String MISS = """
			本次已经完成知识库匹配，没有命中相关资料。
			用户提示词里的「先查询知识库」这一步已经由平台完成，不要再查询，也不要编造知识库内容。
			跳过按知识库回答，继续执行用户提示词中的后续步骤。
			""";

	private KnowledgePrompt() {
	}

	static void append(StringBuilder system, Retrieval retrieval) {
		if (retrieval == null || !retrieval.searched()) {
			return;
		}
		if (system.length() > 0) {
			system.append("\n\n");
		}
		if (!retrieval.hits().isEmpty() && retrieval.prompt() != null && !retrieval.prompt().isBlank()) {
			system.append(retrieval.prompt().trim()).append("\n\n").append(HIT.trim());
			return;
		}
		system.append(MISS.trim());
	}

}
