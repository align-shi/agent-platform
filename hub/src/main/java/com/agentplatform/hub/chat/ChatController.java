package com.agentplatform.hub.chat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final AgentChatService agentChat;
	private final ExecutorService executor = Executors.newCachedThreadPool();

	public ChatController(AgentChatService agentChat) {
		this.agentChat = agentChat;
	}

	@PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter completions(@Valid @RequestBody ChatDtos.Request request) {
		SseEmitter emitter = new SseEmitter(5 * 60 * 1000L);
		executor.submit(() -> {
			try {
				agentChat.stream(request, emitter);
			}
			catch (Exception ex) {
				try {
					emitter.send(SseEmitter.event().name("error").data(ex.getMessage()));
					emitter.complete();
				}
				catch (Exception ignored) {
					emitter.completeWithError(ex);
				}
			}
		});
		return emitter;
	}

}
