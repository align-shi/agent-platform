package com.agentplatform.hub.feishu;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.agent.AgentRepository;
import com.agentplatform.hub.crypto.SecretCipher;

@Service
public class FeishuBotService {

	private final FeishuBotRepository bots;
	private final FeishuThreadRepository threads;
	private final FeishuSeenEventRepository seen;
	private final AgentRepository agents;
	private final SecretCipher cipher;
	private final FeishuClient client;
	private final TransactionTemplate transactions;

	public FeishuBotService(
			FeishuBotRepository bots,
			FeishuThreadRepository threads,
			FeishuSeenEventRepository seen,
			AgentRepository agents,
			SecretCipher cipher,
			FeishuClient client,
			PlatformTransactionManager transactionManager) {
		this.bots = bots;
		this.threads = threads;
		this.seen = seen;
		this.agents = agents;
		this.cipher = cipher;
		this.client = client;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Transactional(readOnly = true)
	public List<FeishuDtos.View> list() {
		return bots.findAll().stream()
				.sorted(Comparator.comparing(FeishuBotEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.map(this::toView)
				.toList();
	}

	@Transactional
	public FeishuDtos.View create(FeishuDtos.UpsertRequest request) {
		FeishuBotEntity entity = new FeishuBotEntity();
		entity.setId(UUID.randomUUID().toString());
		apply(entity, request, true);
		return toView(bots.save(entity));
	}

	@Transactional
	public FeishuDtos.View update(String id, FeishuDtos.UpsertRequest request) {
		FeishuBotEntity entity = require(id);
		apply(entity, request, false);
		return toView(bots.save(entity));
	}

	@Transactional
	public void delete(String id) {
		if (!bots.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "飞书机器人不存在");
		}
		threads.deleteByBotId(id);
		seen.deleteByBotId(id);
		bots.deleteById(id);
	}

	@Transactional(readOnly = true)
	public void probe(String id) {
		Secrets secrets = secrets(id);
		client.probe(secrets.appId(), secrets.appSecret());
	}

	@Transactional(readOnly = true)
	public FeishuBotEntity require(String id) {
		return bots.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "飞书机器人不存在"));
	}

	@Transactional(readOnly = true)
	public Secrets secrets(String id) {
		return toSecrets(require(id));
	}

	@Transactional(readOnly = true)
	public String conversationId(String botId, String chatId) {
		return threads.findByBotIdAndChatId(botId, chatId)
				.map(FeishuThreadEntity::getConversationId)
				.orElse(null);
	}

	@Transactional
	public void bind(String botId, String chatId, String conversationId) {
		FeishuThreadEntity entity = threads.findByBotIdAndChatId(botId, chatId).orElseGet(() -> {
			FeishuThreadEntity created = new FeishuThreadEntity();
			created.setId(UUID.randomUUID().toString());
			created.setBotId(botId);
			created.setChatId(chatId);
			return created;
		});
		entity.setConversationId(conversationId);
		threads.save(entity);
	}

	public boolean claim(String botId, String eventId) {
		if (eventId == null || eventId.isBlank()) {
			return true;
		}
		try {
			Boolean inserted = transactions.execute(status -> {
				if (ThreadLocalRandom.current().nextInt(32) == 0) {
					seen.deleteByCreatedAtBefore(Instant.now().minus(Duration.ofDays(2)));
				}
				if (seen.existsById(eventId)) {
					return false;
				}
				FeishuSeenEventEntity entity = new FeishuSeenEventEntity();
				entity.setEventId(eventId);
				entity.setBotId(botId);
				entity.setCreatedAt(Instant.now());
				seen.saveAndFlush(entity);
				return true;
			});
			return Boolean.TRUE.equals(inserted);
		}
		catch (DataIntegrityViolationException ex) {
			return false;
		}
	}

	@Transactional
	public void markResult(String id, String error) {
		if (!bots.existsById(id)) {
			return;
		}
		FeishuBotEntity entity = require(id);
		entity.setLastEventAt(Instant.now());
		entity.setLastError(abbreviate(error));
		bots.save(entity);
	}

	static String normalizePublicBase(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.trim();
		int api = trimmed.indexOf("/api/feishu/events");
		if (api >= 0) {
			trimmed = trimmed.substring(0, api);
		}
		while (trimmed.endsWith("/")) {
			trimmed = trimmed.substring(0, trimmed.length() - 1);
		}
		if (!trimmed.startsWith("https://")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公网地址需要以 https:// 开头");
		}
		if (trimmed.length() > 300) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公网地址过长");
		}
		return trimmed;
	}

	private void apply(FeishuBotEntity entity, FeishuDtos.UpsertRequest request, boolean creating) {
		String name = required(request.name(), "名称");
		if (name.length() > 80) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称最多 80 个字");
		}
		String appId = required(request.appId(), "App ID");
		if (appId.length() > 64) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "App ID 过长");
		}
		ensureAppIdUnique(appId, creating ? null : entity.getId());
		String agentId = required(request.agentId(), "智能体");
		if (!agents.existsById(agentId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择一个智能体");
		}
		entity.setName(name);
		entity.setAppId(appId);
		entity.setAgentId(agentId);
		entity.setEnabled(request.enabled());
		entity.setPublicBaseUrl(normalizePublicBase(request.publicBaseUrl()));
		applySecret(entity, request.appSecret(), creating);
		applyToken(entity, request.verificationToken(), creating);
		applyEncrypt(entity, request.encryptEnabled(), request.encryptKey(), creating);
	}

	private void applySecret(FeishuBotEntity entity, String appSecret, boolean creating) {
		if (appSecret != null && !appSecret.isBlank()) {
			String value = appSecret.trim();
			entity.setAppSecretCipher(cipher.encrypt(value));
			entity.setSecretLast4(last4(value));
			return;
		}
		if (creating || entity.getAppSecretCipher() == null || entity.getAppSecretCipher().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 App Secret");
		}
	}

	private void applyToken(FeishuBotEntity entity, String verificationToken, boolean creating) {
		if (verificationToken != null && !verificationToken.isBlank()) {
			String value = verificationToken.trim();
			entity.setVerificationTokenCipher(cipher.encrypt(value));
			entity.setTokenLast4(last4(value));
			return;
		}
		if (creating || entity.getVerificationTokenCipher() == null || entity.getVerificationTokenCipher().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 Verification Token");
		}
	}

	private void applyEncrypt(FeishuBotEntity entity, boolean enabled, String encryptKey, boolean creating) {
		if (!enabled) {
			entity.setEncryptKeyCipher(null);
			return;
		}
		if (encryptKey != null && !encryptKey.isBlank()) {
			entity.setEncryptKeyCipher(cipher.encrypt(encryptKey.trim()));
			return;
		}
		if (creating || entity.getEncryptKeyCipher() == null || entity.getEncryptKeyCipher().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 Encrypt Key，或关闭事件加密");
		}
	}

	private void ensureAppIdUnique(String appId, String selfId) {
		bots.findByAppId(appId).ifPresent((existing) -> {
			if (selfId == null || !existing.getId().equals(selfId)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这个 App ID 已经添加过了");
			}
		});
	}

	private Secrets toSecrets(FeishuBotEntity entity) {
		String encrypt = entity.getEncryptKeyCipher() == null || entity.getEncryptKeyCipher().isBlank()
				? ""
				: cipher.decrypt(entity.getEncryptKeyCipher());
		return new Secrets(
				entity.getAppId(),
				cipher.decrypt(entity.getAppSecretCipher()),
				cipher.decrypt(entity.getVerificationTokenCipher()),
				encrypt,
				entity.getAgentId(),
				entity.isEnabled());
	}

	private FeishuDtos.View toView(FeishuBotEntity entity) {
		String path = "/api/feishu/events/" + entity.getId();
		String base = entity.getPublicBaseUrl();
		return new FeishuDtos.View(
				entity.getId(),
				entity.getName(),
				entity.getAppId(),
				entity.getSecretLast4() == null ? "" : entity.getSecretLast4(),
				entity.getAppSecretCipher() != null && !entity.getAppSecretCipher().isBlank(),
				entity.getTokenLast4() == null ? "" : entity.getTokenLast4(),
				entity.getVerificationTokenCipher() != null && !entity.getVerificationTokenCipher().isBlank(),
				entity.getEncryptKeyCipher() != null && !entity.getEncryptKeyCipher().isBlank(),
				entity.getAgentId(),
				agents.findById(entity.getAgentId()).map(agent -> agent.getName()).orElse(""),
				entity.isEnabled(),
				base == null ? "" : base,
				path,
				base == null ? null : base + path,
				entity.getLastError(),
				entity.getLastEventAt());
	}

	private static String required(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写" + label);
		}
		return value.trim();
	}

	private static String last4(String value) {
		return value.length() <= 4 ? value : value.substring(value.length() - 4);
	}

	private static String abbreviate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String compact = value.replaceAll("\\s+", " ").trim();
		return compact.length() <= 500 ? compact : compact.substring(0, 500);
	}

	public record Secrets(
			String appId,
			String appSecret,
			String verificationToken,
			String encryptKey,
			String agentId,
			boolean enabled) {
	}

}
