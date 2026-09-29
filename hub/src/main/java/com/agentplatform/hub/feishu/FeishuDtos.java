package com.agentplatform.hub.feishu;

import java.time.Instant;

public final class FeishuDtos {

	private FeishuDtos() {
	}

	public record UpsertRequest(
			String name,
			String appId,
			String appSecret,
			String verificationToken,
			String encryptKey,
			boolean encryptEnabled,
			String agentId,
			boolean enabled,
			String publicBaseUrl) {
	}

	public record View(
			String id,
			String name,
			String appId,
			String secretLast4,
			boolean secretConfigured,
			String tokenLast4,
			boolean tokenConfigured,
			boolean encryptConfigured,
			String agentId,
			String agentName,
			boolean enabled,
			String publicBaseUrl,
			String callbackPath,
			String callbackUrl,
			String lastError,
			Instant lastEventAt) {
	}

}
