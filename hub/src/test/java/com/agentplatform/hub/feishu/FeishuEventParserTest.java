package com.agentplatform.hub.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class FeishuEventParserTest {

	private final JsonMapper mapper = new JsonMapper();

	@Test
	void readsUrlVerification() {
		FeishuPayload payload = FeishuEventParser.parse("""
				{"type":"url_verification","token":"tok","challenge":"abc"}
				""");
		FeishuPayload.Challenge challenge = assertInstanceOf(FeishuPayload.Challenge.class, payload);
		assertEquals("tok", challenge.token());
		assertEquals("abc", challenge.challenge());
	}

	@Test
	void stripsMentionFromTextMessage() throws Exception {
		String content = mapper.writeValueAsString(Map.of("text", "@_user_1 你好"));
		String json = mapper.writeValueAsString(Map.of(
				"schema", "2.0",
				"header", Map.of("event_id", "evt1", "event_type", "im.message.receive_v1", "token", "tok"),
				"event", Map.of(
						"sender", Map.of("sender_type", "user"),
						"message", Map.of(
								"message_id", "om_1",
								"chat_id", "oc_1",
								"message_type", "text",
								"content", content,
								"mentions", List.of(Map.of("key", "@_user_1", "name", "bot"))))));

		FeishuPayload.TextMessage message = assertInstanceOf(FeishuPayload.TextMessage.class, FeishuEventParser.parse(json));
		assertEquals("你好", message.text());
		assertEquals("evt1", message.eventId());
		assertEquals("om_1", message.messageId());
	}

	@Test
	void asksForTextWhenMessageIsNotText() throws Exception {
		String json = mapper.writeValueAsString(Map.of(
				"header", Map.of("event_id", "evt2", "event_type", "im.message.receive_v1", "token", "tok"),
				"event", Map.of(
						"sender", Map.of("sender_type", "user"),
						"message", Map.of("message_id", "om_2", "chat_id", "oc_2", "message_type", "image"))));
		FeishuPayload.Notice notice = assertInstanceOf(FeishuPayload.Notice.class, FeishuEventParser.parse(json));
		assertTrue(notice.reply().contains("文字"));
	}

	@Test
	void ignoresBotOwnMessagesAndOtherEvents() throws Exception {
		String own = mapper.writeValueAsString(Map.of(
				"header", Map.of("event_id", "evt3", "event_type", "im.message.receive_v1", "token", "tok"),
				"event", Map.of(
						"sender", Map.of("sender_type", "app"),
						"message", Map.of("message_id", "om_3", "chat_id", "oc_3", "message_type", "text"))));
		assertInstanceOf(FeishuPayload.Ignore.class, FeishuEventParser.parse(own));

		String other = mapper.writeValueAsString(Map.of(
				"header", Map.of("event_type", "im.chat.member.bot.added_v1", "token", "tok"),
				"event", Map.of()));
		assertInstanceOf(FeishuPayload.Ignore.class, FeishuEventParser.parse(other));
	}

	@Test
	void splitsLongRepliesAndKeepsATruncationNote() {
		String text = "a".repeat(201);
		List<String> parts = FeishuTexts.chunks(text, 40);
		assertEquals(5, parts.size());
		assertTrue(parts.get(4).contains("已截断"));
		assertEquals(List.of("（没有生成回复）"), FeishuTexts.chunks("  ", 40));
	}

}
