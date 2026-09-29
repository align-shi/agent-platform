package com.agentplatform.hub.chat;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/open/agents")
public class OpenChatController {

	private final AgentChatService agentChat;

	public OpenChatController(AgentChatService agentChat) {
		this.agentChat = agentChat;
	}

	@PostMapping("/{code}/messages")
	public Reply message(@PathVariable String code, @Valid @RequestBody Request request) throws Exception {
		ChatDtos.Reply reply = agentChat.completeByCode(code, request.conversationId(), request.content());
		return new Reply(reply.agentCode(), reply.conversationId(), reply.content(), reply.callId());
	}

	public record Request(String conversationId, @NotBlank String content) {
	}

	public record Reply(String agentCode, String conversationId, String content, String callId) {
	}

}
