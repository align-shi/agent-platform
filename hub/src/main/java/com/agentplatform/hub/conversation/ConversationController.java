package com.agentplatform.hub.conversation;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ConversationController {

	private final ConversationService conversations;

	public ConversationController(ConversationService conversations) {
		this.conversations = conversations;
	}

	@GetMapping("/agents/{agentId}/conversations")
	public List<ConversationDtos.View> list(@PathVariable String agentId) {
		return conversations.listByAgent(agentId);
	}

	@GetMapping("/conversations/{id}/messages")
	public List<ConversationDtos.MessageView> messages(@PathVariable String id) {
		return conversations.messages(id);
	}

	@DeleteMapping("/conversations/{id}")
	public void delete(@PathVariable String id) {
		conversations.delete(id);
	}

}
