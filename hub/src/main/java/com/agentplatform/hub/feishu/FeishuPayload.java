package com.agentplatform.hub.feishu;

public sealed interface FeishuPayload {

	String token();

	record Challenge(String token, String challenge) implements FeishuPayload {
	}

	record TextMessage(String token, String eventId, String messageId, String chatId, String text) implements FeishuPayload {
	}

	record Notice(String token, String eventId, String messageId, String chatId, String reply) implements FeishuPayload {
	}

	record Ignore(String token, String reason) implements FeishuPayload {
	}

}
