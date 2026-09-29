package com.agentplatform.hub.conversation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.trace.CallTraceService;

@Service
public class ConversationService {

	private final ConversationRepository conversations;
	private final MessageRepository messages;
	private final CallTraceService calls;

	public ConversationService(
			ConversationRepository conversations,
			MessageRepository messages,
			CallTraceService calls) {
		this.conversations = conversations;
		this.messages = messages;
		this.calls = calls;
	}

	@Transactional(readOnly = true)
	public List<ConversationDtos.View> listByAgent(String agentId) {
		return conversations.findByAgentIdOrderByUpdatedAtDesc(agentId).stream()
				.map(this::toView)
				.toList();
	}

	@Transactional(readOnly = true)
	public ConversationEntity require(String id) {
		return conversations.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
	}

	@Transactional(readOnly = true)
	public List<ConversationDtos.MessageView> messages(String conversationId) {
		require(conversationId);
		return messages.findByConversationIdOrderBySeqAsc(conversationId).stream()
				.map(item -> new ConversationDtos.MessageView(
						item.getId(),
						item.getRole(),
						item.getContent(),
						CitationJson.decode(item.getCitations())))
				.toList();
	}

	@Transactional
	public ConversationEntity getOrCreate(String agentId, String conversationId) {
		if (conversationId == null || conversationId.isBlank()) {
			ConversationEntity created = new ConversationEntity();
			created.setId(UUID.randomUUID().toString());
			created.setAgentId(agentId);
			created.setTitle("新对话");
			return conversations.save(created);
		}
		ConversationEntity existing = require(conversationId);
		if (!existing.getAgentId().equals(agentId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conversation does not belong to this agent");
		}
		return existing;
	}

	@Transactional
	public MessageEntity append(String conversationId, String role, String content) {
		return append(conversationId, role, content, null);
	}

	@Transactional
	public MessageEntity append(String conversationId, String role, String content, String citations) {
		ConversationEntity conversation = require(conversationId);
		long count = messages.countByConversationId(conversationId);
		MessageEntity message = new MessageEntity();
		message.setId(UUID.randomUUID().toString());
		message.setConversationId(conversationId);
		message.setRole(role);
		message.setContent(content);
		message.setCitations(citations == null || citations.isBlank() ? null : citations);
		message.setSeq((int) count + 1);
		if ("user".equals(role) && "新对话".equals(conversation.getTitle())) {
			conversation.setTitle(titleFrom(content));
		}
		conversation.touch();
		conversations.save(conversation);
		return messages.save(message);
	}

	@Transactional
	public void saveSummary(String id, String summary, int summaryUpToSeq) {
		ConversationEntity conversation = require(id);
		conversation.setSummary(summary);
		conversation.setSummaryUpToSeq(Math.max(0, summaryUpToSeq));
		conversation.touch();
		conversations.save(conversation);
	}

	@Transactional
	public void delete(String id) {
		require(id);
		messages.deleteByConversationId(id);
		calls.deleteByConversationId(id);
		conversations.deleteById(id);
	}

	@Transactional
	public void deleteByAgentId(String agentId) {
		for (ConversationEntity conversation : conversations.findByAgentIdOrderByUpdatedAtDesc(agentId)) {
			messages.deleteByConversationId(conversation.getId());
		}
		calls.deleteByAgentId(agentId);
		conversations.deleteByAgentId(agentId);
	}

	private ConversationDtos.View toView(ConversationEntity entity) {
		return new ConversationDtos.View(entity.getId(), entity.getAgentId(), entity.getTitle(), entity.getUpdatedAt());
	}

	private static String titleFrom(String content) {
		String compact = content.replaceAll("\\s+", " ").trim();
		if (compact.length() <= 24) {
			return compact.isEmpty() ? "新对话" : compact;
		}
		return compact.substring(0, 24);
	}

}
