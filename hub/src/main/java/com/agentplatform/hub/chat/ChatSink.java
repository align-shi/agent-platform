package com.agentplatform.hub.chat;

import java.io.IOException;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

interface ChatSink {

	void meta(String conversationId) throws IOException;

	void status(String text) throws IOException;

	void delta(String sseData) throws IOException;

	void citations(String json) throws IOException;

	void done() throws IOException;

	static ChatSink silent() {
		return new ChatSink() {
			@Override
			public void meta(String conversationId) {
			}

			@Override
			public void status(String text) {
			}

			@Override
			public void delta(String sseData) {
			}

			@Override
			public void citations(String json) {
			}

			@Override
			public void done() {
			}
		};
	}

	static ChatSink sse(SseEmitter emitter) {
		return new ChatSink() {
			@Override
			public void meta(String conversationId) throws IOException {
				emitter.send(SseEmitter.event().name("meta").data("{\"conversationId\":\"" + conversationId + "\"}"));
			}

			@Override
			public void status(String text) throws IOException {
				emitter.send(SseEmitter.event().name("status").data(text));
			}

			@Override
			public void delta(String sseData) throws IOException {
				emitter.send(SseEmitter.event().data(sseData));
			}

			@Override
			public void citations(String json) throws IOException {
				emitter.send(SseEmitter.event().name("citations").data(json));
			}

			@Override
			public void done() throws IOException {
				emitter.send(SseEmitter.event().name("done").data("[DONE]"));
				emitter.complete();
			}
		};
	}

}
