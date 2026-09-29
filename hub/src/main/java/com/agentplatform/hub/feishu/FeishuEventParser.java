package com.agentplatform.hub.feishu;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class FeishuEventParser {

	private static final JsonMapper MAPPER = new JsonMapper();
	private static final String TEXT_ONLY = "目前只能读文字，请发文字消息。";

	private FeishuEventParser() {
	}

	public static FeishuPayload parse(String json) {
		try {
			return parse(MAPPER.readTree(json));
		}
		catch (JacksonException ex) {
			throw new IllegalArgumentException("飞书请求不是合法 JSON");
		}
	}

	public static FeishuPayload parse(JsonNode root) {
		if (root == null || root.isNull()) {
			throw new IllegalArgumentException("飞书请求不是合法 JSON");
		}
		if ("url_verification".equals(root.path("type").asText()) || root.hasNonNull("challenge")) {
			String challenge = root.path("challenge").asText("");
			if (challenge.isBlank()) {
				throw new IllegalArgumentException("飞书 URL 校验缺少 challenge");
			}
			return new FeishuPayload.Challenge(root.path("token").asText(""), challenge);
		}
		String token = root.path("header").path("token").asText("");
		String eventType = root.path("header").path("event_type").asText("");
		if (!"im.message.receive_v1".equals(eventType)) {
			return new FeishuPayload.Ignore(token, "event " + eventType);
		}
		JsonNode event = root.path("event");
		String senderType = event.path("sender").path("sender_type").asText("");
		if (!senderType.isBlank() && !"user".equals(senderType)) {
			return new FeishuPayload.Ignore(token, "sender " + senderType);
		}
		JsonNode message = event.path("message");
		String eventId = root.path("header").path("event_id").asText("");
		String messageId = message.path("message_id").asText("");
		String chatId = message.path("chat_id").asText("");
		if (messageId.isBlank() || chatId.isBlank()) {
			return new FeishuPayload.Ignore(token, "missing message target");
		}
		if (!"text".equals(message.path("message_type").asText(""))) {
			return new FeishuPayload.Notice(token, eventId, messageId, chatId, TEXT_ONLY);
		}
		String text = FeishuTexts.userText(message.get("content"), message.get("mentions"));
		return new FeishuPayload.TextMessage(token, eventId, messageId, chatId, text);
	}

}
