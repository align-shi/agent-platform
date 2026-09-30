package com.agentplatform.hub.feishu;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class FeishuClient {

	static final String OPEN_BASE = "https://open.feishu.cn";

	private final RestClient restClient;
	private final JsonMapper mapper;
	private final ConcurrentHashMap<String, CachedToken> tokens = new ConcurrentHashMap<>();

	public FeishuClient(JsonMapper mapper) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
		factory.setReadTimeout(Duration.ofSeconds(20));
		this.restClient = RestClient.builder().requestFactory(factory).build();
		this.mapper = mapper;
	}

	public void probe(String appId, String appSecret) {
		tenantToken(appId, appSecret, true);
	}

	public String botName(String appId, String appSecret) {
		String token = tenantToken(appId, appSecret, false);
		Map<String, Object> response = get(URI.create(OPEN_BASE + "/open-apis/bot/v3/info"), token, "获取飞书机器人名称失败");
		ensureOk(response, "获取飞书机器人名称失败");
		Object bot = response.get("bot");
		if (bot instanceof Map<?, ?> map && map.get("app_name") instanceof String name && !name.isBlank()) {
			return name.trim();
		}
		throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "飞书没有返回机器人名称");
	}

	public String addReaction(String appId, String appSecret, String messageId, String emojiType) {
		String token = tenantToken(appId, appSecret, false);
		URI uri = messageUri(messageId, "/reactions");
		Map<String, Object> payload = Map.of("reaction_type", Map.of("emoji_type", emojiType));
		Map<String, Object> response = post(uri, token, payload, "飞书表情回复失败");
		ensureOk(response, "飞书表情回复失败");
		Object data = response.get("data");
		if (data instanceof Map<?, ?> map && map.get("reaction_id") instanceof String id && !id.isBlank()) {
			return id;
		}
		return null;
	}

	public void deleteReaction(String appId, String appSecret, String messageId, String reactionId) {
		String token = tenantToken(appId, appSecret, false);
		String reactionPath = UriUtils.encodePathSegment(reactionId, StandardCharsets.UTF_8);
		URI uri = messageUri(messageId, "/reactions/" + reactionPath);
		Map<String, Object> response = delete(uri, token, "飞书表情撤回失败");
		ensureOk(response, "飞书表情撤回失败");
	}

	public void replyText(String appId, String appSecret, String messageId, String text, String eventId) {
		String token = tenantToken(appId, appSecret, false);
		URI uri = messageUri(messageId, "/reply");
		List<String> parts = FeishuTexts.chunks(text, 3500);
		for (int i = 0; i < parts.size(); i++) {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("msg_type", "text");
			payload.put("content", textContent(parts.get(i)));
			payload.put("uuid", replyUuid(eventId, i));
			Map<String, Object> response = post(uri, token, payload, "飞书回复失败");
			ensureOk(response, "飞书回复失败");
		}
	}

	private String tenantToken(String appId, String appSecret, boolean force) {
		String cacheKey = appId;
		if (!force) {
			CachedToken cached = tokens.get(cacheKey);
			if (cached != null
					&& cached.secret.equals(appSecret)
					&& cached.expiresAt.isAfter(Instant.now().plusSeconds(120))) {
				return cached.value;
			}
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("app_id", appId);
		body.put("app_secret", appSecret);
		Map<String, Object> response = post(
				URI.create(OPEN_BASE + "/open-apis/auth/v3/tenant_access_token/internal"),
				null,
				body,
				"飞书鉴权失败");
		ensureOk(response, "飞书鉴权失败");
		Object token = response.get("tenant_access_token");
		if (!(token instanceof String value) || value.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "飞书鉴权没有返回 token");
		}
		Object expire = response.get("expire");
		long seconds = expire instanceof Number number ? number.longValue() : 7200L;
		tokens.put(cacheKey, new CachedToken(appSecret, value, Instant.now().plusSeconds(Math.max(60, seconds))));
		return value;
	}

	private URI messageUri(String messageId, String suffix) {
		String messagePath = UriUtils.encodePathSegment(messageId, StandardCharsets.UTF_8);
		return URI.create(OPEN_BASE + "/open-apis/im/v1/messages/" + messagePath + suffix);
	}

	private Map<String, Object> get(URI url, String bearer, String action) {
		try {
			return restClient.get()
					.uri(url)
					.header("Authorization", "Bearer " + bearer)
					.retrieve()
					.body(new org.springframework.core.ParameterizedTypeReference<>() {
					});
		}
		catch (RestClientResponseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, action + "：" + abbreviate(ex.getResponseBodyAsString()));
		}
		catch (RestClientException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, action + "：" + abbreviate(ex.getMessage()));
		}
	}

	private Map<String, Object> post(URI url, String bearer, Map<String, Object> body, String action) {
		try {
			RestClient.RequestBodySpec spec = restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON);
			if (bearer != null) {
				spec = spec.header("Authorization", "Bearer " + bearer);
			}
			return spec.body(body).retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() {
			});
		}
		catch (RestClientResponseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, action + "：" + abbreviate(ex.getResponseBodyAsString()));
		}
		catch (RestClientException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, action + "：" + abbreviate(ex.getMessage()));
		}
	}

	private Map<String, Object> delete(URI url, String bearer, String action) {
		try {
			return restClient.delete()
					.uri(url)
					.header("Authorization", "Bearer " + bearer)
					.retrieve()
					.body(new org.springframework.core.ParameterizedTypeReference<>() {
					});
		}
		catch (RestClientResponseException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, action + "：" + abbreviate(ex.getResponseBodyAsString()));
		}
		catch (RestClientException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, action + "：" + abbreviate(ex.getMessage()));
		}
	}

	private String textContent(String text) {
		try {
			return mapper.writeValueAsString(Map.of("text", text));
		}
		catch (JacksonException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void ensureOk(Map<String, Object> response, String action) {
		if (response == null) {
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, action + "：没有返回结果");
		}
		Object code = response.get("code");
		int numeric = code instanceof Number number ? number.intValue() : -1;
		if (numeric != 0) {
			Object msg = response.get("msg");
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, action + "：" + (msg == null ? "未知错误" : msg));
		}
	}

	private static String replyUuid(String eventId, int index) {
		String raw = (eventId == null || eventId.isBlank() ? "feishu" : eventId) + "#" + index;
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest).substring(0, 32);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String abbreviate(String value) {
		if (value == null || value.isBlank()) {
			return "未知错误";
		}
		String compact = value.replaceAll("\\s+", " ").trim();
		return compact.length() <= 300 ? compact : compact.substring(0, 300);
	}

	private record CachedToken(String secret, String value, Instant expiresAt) {
	}

}
