package com.agentplatform.hub.memory;

import java.util.List;

import com.agentplatform.hub.agent.MemoryMode;
import com.agentplatform.hub.conversation.ConversationDtos;

final class MemoryPlanner {

	static final int DEFAULT_TOKEN_LIMIT = 8000;
	static final int DEFAULT_KEEP_LAST = 10;

	private MemoryPlanner() {
	}

	static int estimateTokens(String text) {
		if (text == null || text.isBlank()) {
			return 0;
		}
		return Math.max(1, (text.length() + 1) / 2);
	}

	static int estimateTokens(String summary, List<ConversationDtos.MessageView> messages) {
		int total = estimateTokens(summary);
		if (messages == null) {
			return total;
		}
		for (ConversationDtos.MessageView message : messages) {
			total += estimateTokens(message.content());
		}
		return total;
	}

	static int tokenLimit(Integer configured) {
		if (configured == null) {
			return DEFAULT_TOKEN_LIMIT;
		}
		return Math.min(200000, Math.max(1000, configured));
	}

	static int keepLast(Integer configured) {
		if (configured == null) {
			return DEFAULT_KEEP_LAST;
		}
		return Math.min(100, Math.max(2, configured));
	}

	static List<ConversationDtos.MessageView> historyForModel(
			MemoryMode mode,
			List<ConversationDtos.MessageView> all,
			int summaryUpToSeq) {
		if (all == null || all.isEmpty()) {
			return List.of();
		}
		if (mode == MemoryMode.NONE) {
			return List.of(all.get(all.size() - 1));
		}
		int from = Math.min(Math.max(summaryUpToSeq, 0), all.size());
		return List.copyOf(all.subList(from, all.size()));
	}

	static boolean needsCompact(int estimatedTokens, int tokenLimit, int unsummarizedCount, int keepLast) {
		return unsummarizedCount > keepLast && estimatedTokens >= tokenLimit;
	}

	static int compactEndIndex(int summaryUpToSeq, int total, int keepLast) {
		return Math.max(summaryUpToSeq, total - keepLast);
	}

}
