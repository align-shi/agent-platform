package com.agentplatform.hub.feishu;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class FeishuLinkStatus {

	public static final String CONNECTED = "connected";
	public static final String CONNECTING = "connecting";
	public static final String RECONNECTING = "reconnecting";
	public static final String FAILED = "failed";
	public static final String STOPPED = "stopped";

	private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();

	public record Snapshot(String state, String detail) {
	}

	public void mark(String botId, String state, String detail) {
		snapshots.put(botId, new Snapshot(state, detail));
	}

	public void clear(String botId) {
		snapshots.remove(botId);
	}

	public Snapshot get(String botId) {
		return snapshots.getOrDefault(botId, new Snapshot(CONNECTING, null));
	}

}
