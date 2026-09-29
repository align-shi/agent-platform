package com.agentplatform.hub.feishu;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.agent.AgentEntity;
import com.agentplatform.hub.agent.AgentService;
import com.agentplatform.hub.chat.AgentChatService;
import com.agentplatform.hub.chat.ChatDtos;
import com.agentplatform.hub.conversation.ConversationService;
import com.agentplatform.hub.feishu.FeishuBotService.Secrets;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import jakarta.annotation.PreDestroy;

@Service
public class FeishuInboundService {

	private static final Logger log = LoggerFactory.getLogger(FeishuInboundService.class);

	private final FeishuBotService bots;
	private final AgentService agents;
	private final AgentChatService agentChat;
	private final ConversationService conversations;
	private final FeishuClient client;
	private final JsonMapper mapper;
	private final ExecutorService executor = Executors.newFixedThreadPool(4, (runnable) -> {
		Thread thread = new Thread(runnable, "feishu-inbound");
		thread.setDaemon(true);
		return thread;
	});
	private final ConcurrentHashMap<String, Object> chatLocks = new ConcurrentHashMap<>();

	public FeishuInboundService(
			FeishuBotService bots,
			AgentService agents,
			AgentChatService agentChat,
			ConversationService conversations,
			FeishuClient client,
			JsonMapper mapper) {
		this.bots = bots;
		this.agents = agents;
		this.agentChat = agentChat;
		this.conversations = conversations;
		this.client = client;
		this.mapper = mapper;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	public Map<String, Object> accept(
			String botId,
			String rawBody,
			String timestamp,
			String nonce,
			String signature) {
		if (rawBody == null || rawBody.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书请求体为空");
		}
		Secrets secrets = bots.secrets(botId);
		JsonNode root = unwrap(rawBody, secrets.encryptKey(), timestamp, nonce, signature);
		FeishuPayload payload;
		try {
			payload = FeishuEventParser.parse(root);
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
		checkToken(secrets.verificationToken(), payload.token());
		if (payload instanceof FeishuPayload.Challenge challenge) {
			log.info("feishu url verified bot={}", botId);
			return Map.of("challenge", challenge.challenge());
		}
		if (!secrets.enabled()) {
			return Map.of();
		}
		if (payload instanceof FeishuPayload.TextMessage text) {
			schedule(botId, text.eventId(), text.chatId(), () -> deliverText(botId, text));
		}
		else if (payload instanceof FeishuPayload.Notice notice) {
			schedule(botId, notice.eventId(), notice.chatId(), () -> deliverNotice(botId, notice));
		}
		return Map.of();
	}

	private void schedule(String botId, String eventId, String chatId, Runnable task) {
		if (!bots.claim(botId, eventId)) {
			return;
		}
		Object lock = chatLocks.computeIfAbsent(botId + ":" + chatId, (key) -> new Object());
		try {
			executor.execute(() -> {
				synchronized (lock) {
					task.run();
				}
			});
		}
		catch (RejectedExecutionException ex) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "飞书消息队列已关闭");
		}
	}

	private void deliverText(String botId, FeishuPayload.TextMessage message) {
		try {
			Secrets secrets = bots.secrets(botId);
			if (!secrets.enabled()) {
				return;
			}
			if (message.text() == null || message.text().isBlank()) {
				reply(secrets, message.messageId(), "请直接发送要问的内容。", message.eventId());
				bots.markResult(botId, null);
				return;
			}
			AgentEntity agent;
			try {
				agent = agents.require(secrets.agentId());
			}
			catch (ResponseStatusException ex) {
				fail(botId, secrets, message.messageId(), message.eventId(), "绑定的智能体已经不存在，请重新选择");
				return;
			}
			if (agent.getCode() == null || agent.getCode().isBlank()) {
				fail(botId, secrets, message.messageId(), message.eventId(), "这个智能体还没有唯一编码");
				return;
			}
			log.info("feishu message bot={} chat={} message={}", botId, message.chatId(), message.messageId());
			ChatDtos.Reply reply = complete(agent.getCode(), botId, message.chatId(), secrets.agentId(), message.text());
			try {
				reply(secrets, message.messageId(), reply.content(), message.eventId());
				bots.markResult(botId, null);
			}
			catch (RuntimeException ex) {
				log.warn("feishu reply failed bot={} message={}", botId, message.messageId(), ex);
				bots.markResult(botId, "智能体已生成回复，但发到飞书失败：" + userFacing(ex));
			}
		}
		catch (Exception ex) {
			log.warn("feishu agent failed bot={} message={}", botId, message.messageId(), ex);
			try {
				Secrets secrets = bots.secrets(botId);
				fail(botId, secrets, message.messageId(), message.eventId(), userFacing(ex));
			}
			catch (RuntimeException nested) {
				bots.markResult(botId, userFacing(ex));
			}
		}
	}

	private void deliverNotice(String botId, FeishuPayload.Notice notice) {
		try {
			Secrets secrets = bots.secrets(botId);
			if (!secrets.enabled()) {
				return;
			}
			reply(secrets, notice.messageId(), notice.reply(), notice.eventId());
			bots.markResult(botId, null);
		}
		catch (RuntimeException ex) {
			log.warn("feishu notice failed bot={} message={}", botId, notice.messageId(), ex);
			bots.markResult(botId, userFacing(ex));
		}
	}

	private ChatDtos.Reply complete(String code, String botId, String chatId, String agentId, String text) throws Exception {
		String conversationId = bots.conversationId(botId, chatId);
		try {
			if (conversationId == null) {
				conversationId = conversations.getOrCreate(agentId, null).getId();
				bots.bind(botId, chatId, conversationId);
			}
			return agentChat.completeByCode(code, conversationId, text);
		}
		catch (ResponseStatusException ex) {
			if (ex.getStatusCode().value() != 404 || !"Conversation not found".equals(ex.getReason())) {
				throw ex;
			}
			String fresh = conversations.getOrCreate(agentId, null).getId();
			bots.bind(botId, chatId, fresh);
			return agentChat.completeByCode(code, fresh, text);
		}
	}

	private void fail(String botId, Secrets secrets, String messageId, String eventId, String reason) {
		try {
			reply(secrets, messageId, "暂时无法回复：" + reason, eventId);
		}
		catch (RuntimeException ex) {
			log.warn("feishu error reply failed message={}", messageId, ex);
			reason = reason + "；回消息也失败了：" + userFacing(ex);
		}
		bots.markResult(botId, reason);
	}

	private void reply(Secrets secrets, String messageId, String text, String eventId) {
		client.replyText(secrets.appId(), secrets.appSecret(), messageId, text, eventId);
	}

	private JsonNode unwrap(String rawBody, String encryptKey, String timestamp, String nonce, String signature) {
		JsonNode root = read(rawBody);
		boolean encrypted = root.hasNonNull("encrypt") && !root.get("encrypt").asText().isBlank();
		boolean signed = signature != null && !signature.isBlank();
		if (encrypted || signed) {
			if (encryptKey == null || encryptKey.isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收到加密事件，但没有配置 Encrypt Key");
			}
			try {
				FeishuCrypto.checkSignature(timestamp, nonce, signature, encryptKey, rawBody, Instant.now().getEpochSecond());
			}
			catch (FeishuCrypto.SignatureException ex) {
				throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage());
			}
		}
		if (!encrypted) {
			return root;
		}
		try {
			return read(FeishuCrypto.decrypt(encryptKey, root.get("encrypt").asText()));
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
	}

	private JsonNode read(String raw) {
		try {
			return mapper.readTree(raw);
		}
		catch (JacksonException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "飞书请求不是合法 JSON");
		}
	}

	private static void checkToken(String expected, String actual) {
		if (expected == null || expected.isBlank()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "还没有配置 Verification Token");
		}
		byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
		byte[] actualBytes = (actual == null ? "" : actual).getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verification Token 不匹配");
		}
	}

	private static String userFacing(Exception ex) {
		if (ex instanceof ResponseStatusException status) {
			String reason = status.getReason();
			if (reason != null && !reason.isBlank()) {
				return reason;
			}
		}
		return "请稍后再试";
	}

}
