package com.agentplatform.hub.feishu;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.context.ApplicationEventPublisher;
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
	private final FeishuLinkStatus links;
	private final ApplicationEventPublisher events;
	private final TransactionTemplate transactions;

	public FeishuBotService(
			FeishuBotRepository bots,
			FeishuThreadRepository threads,
			FeishuSeenEventRepository seen,
			AgentRepository agents,
			SecretCipher cipher,
			FeishuClient client,
			FeishuLinkStatus links,
			ApplicationEventPublisher events,
			PlatformTransactionManager transactionManager) {
		this.bots = bots;
		this.threads = threads;
		this.seen = seen;
		this.agents = agents;
		this.cipher = cipher;
		this.client = client;
		this.links = links;
		this.events = events;
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
		FeishuDtos.View view = toView(bots.save(entity));
		events.publishEvent(new FeishuBotChanged(entity.getId()));
		return view;
	}

	@Transactional
	public FeishuDtos.View update(String id, FeishuDtos.UpsertRequest request) {
		FeishuBotEntity entity = require(id);
		apply(entity, request, false);
		FeishuDtos.View view = toView(bots.save(entity));
		events.publishEvent(new FeishuBotChanged(entity.getId()));
		return view;
	}

	@Transactional
	public void delete(String id) {
		if (!bots.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "飞书机器人不存在");
		}
		threads.deleteByBotId(id);
		seen.deleteByBotId(id);
		bots.deleteById(id);
		links.clear(id);
		events.publishEvent(new FeishuBotChanged(id));
	}

	@Transactional(readOnly = true)
	public void probe(String id) {
		Secrets secrets = secrets(id);
		client.probe(secrets.appId(), secrets.appSecret());
	}

	@Transactional(readOnly = true)
	public String lookupName(FeishuDtos.LookupRequest request) {
		String appId = required(request.appId(), "App ID");
		String secret = request.appSecret();
		if (secret == null || secret.isBlank()) {
			if (request.id() == null || request.id().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写 App Secret");
			}
			secret = secrets(request.id()).appSecret();
		}
		else {
			secret = secret.trim();
		}
		return limitName(client.botName(appId, secret));
	}

	@Transactional
	public void refreshName(String id) {
		FeishuBotEntity entity = bots.findById(id).orElse(null);
		if (entity == null) {
			return;
		}
		String name = limitName(client.botName(entity.getAppId(), cipher.decrypt(entity.getAppSecretCipher())));
		if (name.equals(entity.getName())) {
			return;
		}
		entity.setName(name);
		bots.save(entity);
	}

	@Transactional(readOnly = true)
	public FeishuBotEntity require(String id) {
		return bots.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "飞书机器人不存在"));
	}

	@Transactional(readOnly = true)
	public List<String> botIds() {
		return bots.findAll().stream().map(FeishuBotEntity::getId).toList();
	}

	@Transactional(readOnly = true)
	public Optional<Secrets> findSecrets(String id) {
		return bots.findById(id).map(this::toSecrets);
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

	private void apply(FeishuBotEntity entity, FeishuDtos.UpsertRequest request, boolean creating) {
		String appId = required(request.appId(), "App ID");
		if (appId.length() > 64) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "App ID 过长");
		}
		ensureAppIdUnique(appId, creating ? null : entity.getId());
		String agentId = required(request.agentId(), "智能体");
		if (!agents.existsById(agentId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择一个智能体");
		}
		entity.setAppId(appId);
		entity.setAgentId(agentId);
		entity.setEnabled(request.enabled());
		applySecret(entity, request.appSecret(), creating);
		entity.setName(limitName(client.botName(appId, cipher.decrypt(entity.getAppSecretCipher()))));
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

	private void ensureAppIdUnique(String appId, String selfId) {
		bots.findByAppId(appId).ifPresent((existing) -> {
			if (selfId == null || !existing.getId().equals(selfId)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这个 App ID 已经添加过了");
			}
		});
	}

	private Secrets toSecrets(FeishuBotEntity entity) {
		return new Secrets(
				entity.getId(),
				entity.getAppId(),
				cipher.decrypt(entity.getAppSecretCipher()),
				entity.getAgentId(),
				entity.isEnabled());
	}

	private FeishuDtos.View toView(FeishuBotEntity entity) {
		FeishuLinkStatus.Snapshot link = links.get(entity.getId());
		String linkState = link.state();
		String linkDetail = link.detail();
		if (!entity.isEnabled()) {
			linkState = FeishuLinkStatus.STOPPED;
			linkDetail = null;
		}
		else if (FeishuLinkStatus.STOPPED.equals(linkState)) {
			linkState = FeishuLinkStatus.CONNECTING;
			linkDetail = null;
		}
		return new FeishuDtos.View(
				entity.getId(),
				entity.getName(),
				entity.getAppId(),
				entity.getSecretLast4() == null ? "" : entity.getSecretLast4(),
				entity.getAppSecretCipher() != null && !entity.getAppSecretCipher().isBlank(),
				entity.getAgentId(),
				agents.findById(entity.getAgentId()).map(agent -> agent.getName()).orElse(""),
				entity.isEnabled(),
				linkState,
				linkDetail,
				entity.getLastError(),
				entity.getLastEventAt());
	}

	private static String limitName(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "飞书没有返回机器人名称");
		}
		return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80);
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
			String botId,
			String appId,
			String appSecret,
			String agentId,
			boolean enabled) {
	}

}
