package com.agentplatform.hub.feishu;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.lark.oapi.event.EventDispatcher;
import com.lark.oapi.service.im.ImService;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.ws.Client;
import com.lark.oapi.ws.exception.ClientException;

import jakarta.annotation.PreDestroy;

@Component
@Order(10)
public class FeishuConnectionManager implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(FeishuConnectionManager.class);

	private final FeishuBotService bots;
	private final FeishuInboundService inbound;
	private final FeishuLinkStatus links;
	private final ExecutorService starter = Executors.newCachedThreadPool((runnable) -> {
		Thread thread = new Thread(runnable, "feishu-link");
		thread.setDaemon(true);
		return thread;
	});
	private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

	public FeishuConnectionManager(FeishuBotService bots, FeishuInboundService inbound, FeishuLinkStatus links) {
		this.bots = bots;
		this.inbound = inbound;
		this.links = links;
	}

	@Override
	public void run(ApplicationArguments args) {
		for (String botId : bots.botIds()) {
			sync(botId);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onBotChanged(FeishuBotChanged event) {
		sync(event.botId());
	}

	@PreDestroy
	void shutdown() {
		starter.shutdownNow();
		for (String botId : sessions.keySet()) {
			synchronized (lock(botId)) {
				stopUnlocked(botId);
			}
		}
	}

	private void sync(String botId) {
		try {
			starter.execute(() -> {
				try {
					syncNow(botId);
				}
				catch (Throwable ex) {
					log.error("feishu link worker failed bot={}", botId, ex);
					links.mark(botId, FeishuLinkStatus.FAILED, linkError(ex));
				}
			});
		}
		catch (RuntimeException ex) {
			log.warn("feishu link queue closed bot={}", botId);
		}
	}

	private void syncNow(String botId) {
		Session session;
		synchronized (lock(botId)) {
			Optional<FeishuBotService.Secrets> found = bots.findSecrets(botId);
			if (found.isEmpty() || !found.get().enabled()) {
				stopUnlocked(botId);
				links.mark(botId, FeishuLinkStatus.STOPPED, null);
				return;
			}
			FeishuBotService.Secrets secrets = found.get();
			String fingerprint = secrets.appId() + "\n" + secrets.appSecret();
			Session current = sessions.get(botId);
			String state = links.get(botId).state();
			if (current != null
					&& fingerprint.equals(current.fingerprint)
					&& (FeishuLinkStatus.CONNECTED.equals(state)
							|| FeishuLinkStatus.CONNECTING.equals(state)
							|| FeishuLinkStatus.RECONNECTING.equals(state))) {
				return;
			}
			stopUnlocked(botId);
			links.mark(botId, FeishuLinkStatus.CONNECTING, null);
			session = open(botId, secrets, fingerprint);
			sessions.put(botId, session);
		}
		try {
			session.client.start();
		}
		catch (Exception ex) {
			reject(botId, session, ex);
			return;
		}
		waitUntilReady(botId, session);
	}

	private void waitUntilReady(String botId, Session session) {
		while (isCurrent(botId, session) && !Thread.currentThread().isInterrupted()) {
			try {
				session.client.awaitReady(20_000);
				if (isCurrent(botId, session)) {
					links.mark(botId, FeishuLinkStatus.CONNECTED, null);
					log.info("feishu long connection ready bot={}", botId);
					refreshName(botId);
				}
				return;
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
			catch (Exception ex) {
				if (!failOrKeep(botId, session, ex)) {
					return;
				}
			}
		}
	}

	private void refreshName(String botId) {
		try {
			bots.refreshName(botId);
		}
		catch (RuntimeException ex) {
			log.warn("feishu bot name refresh failed bot={} reason={}", botId, ex.getMessage());
		}
	}

	private void reject(String botId, Session session, Exception ex) {
		if (!isCurrent(botId, session)) {
			return;
		}
		String detail = linkError(ex);
		log.warn("feishu long connection rejected bot={} reason={}", botId, detail);
		links.mark(botId, FeishuLinkStatus.FAILED, detail);
		synchronized (lock(botId)) {
			if (isCurrent(botId, session)) {
				stopUnlocked(botId);
			}
		}
	}

	private boolean failOrKeep(String botId, Session session, Exception ex) {
		if (!isCurrent(botId, session)) {
			return false;
		}
		String detail = linkError(ex);
		if (isAuthFailure(ex)) {
			reject(botId, session, ex);
			return false;
		}
		log.warn("feishu long connection pending bot={} reason={}", botId, detail);
		boolean timedOut = detail.contains("did not complete within");
		links.mark(botId, FeishuLinkStatus.CONNECTING, timedOut ? null : detail);
		if (!timedOut) {
			try {
				Thread.sleep(2000);
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				return false;
			}
		}
		return true;
	}

	private Session open(String botId, FeishuBotService.Secrets secrets, String fingerprint) {
		EventDispatcher dispatcher = EventDispatcher.newBuilder("", "")
				.onP2MessageReceiveV1(new ImService.P2MessageReceiveV1Handler() {
					@Override
					public void handle(P2MessageReceiveV1 event) {
						byte[] body = event.getEventReq() == null ? null : event.getEventReq().getBody();
						if (body == null || body.length == 0) {
							return;
						}
						inbound.onEvent(botId, new String(body, StandardCharsets.UTF_8));
					}
				})
				.build();
		Client client = new Client.Builder(secrets.appId(), secrets.appSecret())
				.eventHandler(dispatcher)
				.onReconnecting(() -> {
					if (sameFingerprint(botId, fingerprint)) {
						links.mark(botId, FeishuLinkStatus.RECONNECTING, null);
					}
				})
				.onReconnected(() -> {
					if (sameFingerprint(botId, fingerprint)) {
						links.mark(botId, FeishuLinkStatus.CONNECTED, null);
						log.info("feishu long connection restored bot={}", botId);
					}
				})
				.build();
		log.info("feishu long connection starting bot={} appId={}", botId, secrets.appId());
		return new Session(fingerprint, client);
	}

	private void stopUnlocked(String botId) {
		Session session = sessions.remove(botId);
		if (session != null) {
			session.client.close();
			log.info("feishu long connection closed bot={}", botId);
		}
	}

	private boolean isCurrent(String botId, Session session) {
		return sessions.get(botId) == session;
	}

	private boolean sameFingerprint(String botId, String fingerprint) {
		Session session = sessions.get(botId);
		return session != null && fingerprint.equals(session.fingerprint);
	}

	private Object lock(String botId) {
		return locks.computeIfAbsent(botId, (key) -> new Object());
	}

	private static boolean isAuthFailure(Throwable error) {
		Throwable current = error;
		while (current != null) {
			if (current instanceof ClientException) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private static String linkError(Throwable error) {
		Throwable current = error;
		while (current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		String message = current.getMessage();
		if (message == null || message.isBlank()) {
			message = current.toString();
		}
		String compact = message.replaceAll("\\s+", " ").trim();
		return compact.length() <= 300 ? compact : compact.substring(0, 300);
	}

	private record Session(String fingerprint, Client client) {
	}

}
