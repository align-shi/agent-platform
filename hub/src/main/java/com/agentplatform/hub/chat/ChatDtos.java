package com.agentplatform.hub.chat;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

public final class ChatDtos {

	private ChatDtos() {
	}

	public record Message(String role, String content) {
	}

	public record Request(
			@NotBlank String agentId,
			String conversationId,
			@NotBlank String content) {
	}

}
