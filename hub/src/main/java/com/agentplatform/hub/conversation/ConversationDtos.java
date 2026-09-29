package com.agentplatform.hub.conversation;

import java.time.Instant;
import java.util.List;

public final class ConversationDtos {

	private ConversationDtos() {
	}

	public record View(String id, String agentId, String title, Instant updatedAt) {
	}

	public record CitationView(String knowledgeBase, String document, String content) {
	}

	public record MessageView(String id, String role, String content, List<CitationView> citations) {

		public MessageView(String id, String role, String content) {
			this(id, role, content, List.of());
		}

	}

	public record MessageList(List<MessageView> messages) {
	}

}
