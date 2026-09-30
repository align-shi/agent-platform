package com.agentplatform.hub.feishu;

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
import jakarta.annotation.PreDestroy;

@Service
public class FeishuInboundService {

	private static final Logger log = LoggerFactory.getLogger(FeishuInboundService.class);

	private final FeishuBotService bots;
	private final AgentService agents;
	private final AgentChatService agentChat;
	private final ConversationService conversations;
	private final FeishuClient client;
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
			FeishuClient client) {
		this.bots = bots;
		this.agents = agents;
		this.agentChat = agentChat;
		this.conversations = conversations;
		this.client = client;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	public void onEvent(String botId, String rawJson) {
		FeishuPayload payload;
		try {
			payload = FeishuEventParser.parse(rawJson);
		}
		catch (RuntimeException ex) {
			log.warn("feishu event ignored bot={} reason={}", botId, ex.getMessage());
			return;
		}
		if (!(payload instanceof FeishuPayload.TextMessage) && !(payload instanceof FeishuPayload.Notice)) {
			return;
		}
		try {
			if (!bots.secrets(botId).enabled()) {
				return;
			}
		}
		catch (ResponseStatusException ex) {
			log.warn("feishu event for missing bot={}", botId);
			return;
		}
		if (payload instanceof FeishuPayload.TextMessage text) {
			schedule(botId, text.eventId(), text.chatId(), () -> deliverText(botId, text));
		}
		else if (payload instanceof FeishuPayload.Notice notice) {
			schedule(botId, notice.eventId(), notice.chatId(), () -> deliverNotice(botId, notice));
		}
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
		Secrets secrets = null;
		String reactionId = null;
		try {
			secrets = bots.secrets(botId);
			if (!secrets.enabled()) {
				return;
			}
			reactionId = beginTyping(secrets, message.messageId());
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
				if (secrets == null) {
					secrets = bots.secrets(botId);
				}
				fail(botId, secrets, message.messageId(), message.eventId(), userFacing(ex));
			}
			catch (RuntimeException nested) {
				bots.markResult(botId, userFacing(ex));
			}
		}
		finally {
			if (secrets != null) {
				endTyping(secrets, message.messageId(), reactionId);
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

	private String beginTyping(Secrets secrets, String messageId) {
		try {
			return client.addReaction(secrets.appId(), secrets.appSecret(), messageId, "Typing");
		}
		catch (RuntimeException ex) {
			log.warn("feishu typing reaction failed message={} reason={}", messageId, userFacing(ex));
			return null;
		}
	}

	private void endTyping(Secrets secrets, String messageId, String reactionId) {
		if (reactionId == null || reactionId.isBlank()) {
			return;
		}
		try {
			client.deleteReaction(secrets.appId(), secrets.appSecret(), messageId, reactionId);
		}
		catch (RuntimeException ex) {
			log.warn("feishu typing reaction clear failed message={} reason={}", messageId, userFacing(ex));
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
