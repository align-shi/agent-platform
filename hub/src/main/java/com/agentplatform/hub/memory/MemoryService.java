package com.agentplatform.hub.memory;

import java.util.List;

import org.springframework.stereotype.Service;

import com.agentplatform.hub.agent.AgentEntity;
import com.agentplatform.hub.agent.AgentService;
import com.agentplatform.hub.agent.MemoryMode;
import com.agentplatform.hub.chat.ChatProxyService;
import com.agentplatform.hub.conversation.ConversationDtos;
import com.agentplatform.hub.conversation.ConversationEntity;
import com.agentplatform.hub.conversation.ConversationService;
import com.agentplatform.hub.provider.ProviderEntity;

@Service
public class MemoryService {

	private final ConversationService conversations;
	private final AgentService agents;
	private final ChatProxyService chatProxy;

	public MemoryService(ConversationService conversations, AgentService agents, ChatProxyService chatProxy) {
		this.conversations = conversations;
		this.agents = agents;
		this.chatProxy = chatProxy;
	}

	public PreparedMemory prepare(
			AgentEntity agent,
			ProviderEntity provider,
			String apiKey,
			String conversationId,
			String baseSystem) {
		MemoryMode mode = agent.resolvedMemoryMode();
		List<ConversationDtos.MessageView> all = conversations.messages(conversationId);
		if (mode.keepsSession()) {
			maybeCompact(agent, provider, apiKey, conversationId, all);
			all = conversations.messages(conversationId);
		}
		ConversationEntity conversation = conversations.require(conversationId);
		String summary = mode.keepsSession() ? conversation.getSummary() : "";
		String facts = mode.keepsCross() ? blankToEmpty(agents.require(agent.getId()).getMemoryFacts()) : "";
		String system = composeSystem(baseSystem, summary, facts);
		return new PreparedMemory(system, MemoryPlanner.historyForModel(mode, all, conversation.resolvedSummaryUpToSeq()));
	}

	public void rememberTurn(AgentEntity agent, ProviderEntity provider, String apiKey, String user, String assistant) {
		if (!agent.resolvedMemoryMode().keepsCross()) {
			return;
		}
		if (blankToEmpty(user).isEmpty() || blankToEmpty(assistant).isEmpty()) {
			return;
		}
		try {
			String existing = blankToEmpty(agents.require(agent.getId()).getMemoryFacts());
			String next = ask(
					provider,
					apiKey,
					agent.getModel(),
					"你是记忆整理助手。根据已有要点和新对话，输出更新后的长期记忆要点，条目化，不超过 800 字。只输出要点。",
					"已有记忆：\n" + (existing.isBlank() ? "（无）" : existing)
							+ "\n\n本轮对话：\n用户：" + user.trim()
							+ "\n助手：" + assistant.trim());
			if (!next.isBlank()) {
				agents.saveFacts(agent.getId(), next);
			}
		}
		catch (RuntimeException ignored) {
			// Keep the last successful facts if the model call fails.
		}
	}

	private void maybeCompact(
			AgentEntity agent,
			ProviderEntity provider,
			String apiKey,
			String conversationId,
			List<ConversationDtos.MessageView> all) {
		ConversationEntity conversation = conversations.require(conversationId);
		int keepLast = MemoryPlanner.keepLast(agent.getKeepLastMessages());
		int tokenLimit = MemoryPlanner.tokenLimit(agent.getSummarizeWhenTokens());
		int upTo = conversation.resolvedSummaryUpToSeq();
		if (upTo > all.size()) {
			upTo = all.size();
		}
		List<ConversationDtos.MessageView> unsummarized = all.subList(upTo, all.size());
		int tokens = MemoryPlanner.estimateTokens(conversation.getSummary(), unsummarized);
		if (!MemoryPlanner.needsCompact(tokens, tokenLimit, unsummarized.size(), keepLast)) {
			return;
		}
		int end = MemoryPlanner.compactEndIndex(upTo, all.size(), keepLast);
		if (end <= upTo) {
			return;
		}
		List<ConversationDtos.MessageView> chunk = all.subList(upTo, end);
		try {
			String nextSummary = summarize(provider, apiKey, agent.getModel(), conversation.getSummary(), chunk);
			conversations.saveSummary(conversationId, nextSummary, end);
			if (agent.resolvedMemoryMode().keepsCross() && !nextSummary.isBlank()) {
				String existing = blankToEmpty(agents.require(agent.getId()).getMemoryFacts());
				String facts = ask(
						provider,
						apiKey,
						agent.getModel(),
						"你是记忆整理助手。把会话摘要合并进长期记忆要点，条目化，不超过 800 字。只输出要点。",
						"已有记忆：\n" + (existing.isBlank() ? "（无）" : existing) + "\n\n会话摘要：\n" + nextSummary);
				if (!facts.isBlank()) {
					agents.saveFacts(agent.getId(), facts);
				}
			}
		}
		catch (RuntimeException ignored) {
			// Fall back to sending the current window; do not fail the user turn.
		}
	}

	private String summarize(
			ProviderEntity provider,
			String apiKey,
			String model,
			String previous,
			List<ConversationDtos.MessageView> chunk) {
		String transcript = transcript(chunk);
		String body = (previous == null || previous.isBlank() ? "" : "已有摘要：\n" + previous + "\n\n")
				+ "待压缩对话：\n" + transcript;
		String summary = ask(
				provider,
				apiKey,
				model,
				"你是对话压缩助手。把内容压成不超过 400 字的中文摘要，保留人名、偏好、未完成事项和结论。只输出摘要。",
				body);
		return summary.isBlank() ? fallbackSummary(previous, transcript) : summary;
	}

	private String ask(ProviderEntity provider, String apiKey, String model, String system, String user) {
		return chatProxy.completeText(provider, apiKey, model, system, user);
	}

	static String composeSystem(String baseSystem, String summary, String facts) {
		StringBuilder system = new StringBuilder();
		if (baseSystem != null && !baseSystem.isBlank()) {
			system.append(baseSystem.trim());
		}
		appendSection(system, "跨会话记忆", facts);
		appendSection(system, "本会话摘要", summary);
		return system.toString();
	}

	private static void appendSection(StringBuilder system, String title, String body) {
		if (body == null || body.isBlank()) {
			return;
		}
		if (system.length() > 0) {
			system.append("\n\n");
		}
		system.append("## ").append(title).append('\n').append(body.trim());
	}

	private static String transcript(List<ConversationDtos.MessageView> messages) {
		StringBuilder text = new StringBuilder();
		for (ConversationDtos.MessageView message : messages) {
			if (text.length() > 0) {
				text.append('\n');
			}
			text.append(message.role()).append(": ").append(message.content() == null ? "" : message.content());
		}
		return text.toString();
	}

	private static String fallbackSummary(String previous, String transcript) {
		String merged = (previous == null ? "" : previous + "\n") + transcript;
		String compact = merged.replaceAll("\\s+", " ").trim();
		if (compact.length() <= 400) {
			return compact;
		}
		return compact.substring(0, 400);
	}

	private static String blankToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

}
