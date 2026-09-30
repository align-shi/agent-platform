package com.agentplatform.hub.feishu;

import java.time.Instant;

public final class FeishuDtos {

	private FeishuDtos() {
	}

	public record UpsertRequest(
			String appId,
			String appSecret,
			String agentId,
			boolean enabled) {
	}

	public record LookupRequest(String id, String appId, String appSecret) {
	}

	public record View(
			String id,
			String name,
			String appId,
			String secretLast4,
			boolean secretConfigured,
			String agentId,
			String agentName,
			boolean enabled,
			String linkStatus,
			String linkDetail,
			String lastError,
			Instant lastEventAt) {
	}

}
