package com.agentplatform.hub.mcp;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class McpClient {

	private static final String PROTOCOL = "2025-03-26";
	private static final String PROTOCOL_FALLBACK = "2024-11-05";
	private static final int MAX_BODY = 100000;

	public Handshake handshake(McpTarget target) {
		try (Session session = Session.open(target)) {
			return session.handshake;
		}
		catch (RuntimeException ex) {
			throw wrap("MCP 初始化失败", ex);
		}
	}

	public List<McpTool> listTools(McpTarget target) {
		try (Session session = Session.open(target)) {
			return session.listTools();
		}
		catch (RuntimeException ex) {
			throw wrap("MCP tools/list 失败", ex);
		}
	}

	public String callTool(McpTarget target, String name, Map<String, Object> arguments) {
		try (Session session = Session.open(target)) {
			return session.callTool(name, arguments == null ? Map.of() : arguments);
		}
		catch (RuntimeException ex) {
			return "ERROR: " + message(ex);
		}
	}

	public DiscoverResult discover(McpTarget target) {
		try (Session session = Session.open(target)) {
			List<McpTool> tools = session.listTools();
			return new DiscoverResult(session.handshake, tools, null);
		}
		catch (RuntimeException ex) {
			throw wrap("无法连接 MCP 服务", ex);
		}
	}

	private static RuntimeException wrap(String prefix, RuntimeException ex) {
		return new IllegalStateException(prefix + ": " + message(ex), ex);
	}

	private static String message(Throwable ex) {
		Throwable current = ex;
		while (current != null) {
			if (current.getMessage() != null && !current.getMessage().isBlank()) {
				return current.getMessage();
			}
			current = current.getCause();
		}
		return ex.getClass().getSimpleName();
	}

	public record McpTarget(String url, String transport, String authType, String authHeader, String secret, int timeoutMs) {
	}

	public record Handshake(String protocolVersion, String serverName, String serverVersion) {
	}

	public record McpTool(String name, String description, Map<String, Object> inputSchema) {
	}

	public record DiscoverResult(Handshake handshake, List<McpTool> tools, String error) {
	}

	private static final class Session implements AutoCloseable {
		private final McpTarget target;
		private final RestClient rest;
		private final URI postUrl;
		private final Handshake handshake;
		private String sessionId;
		private int nextId = 1;
		private InputStream sseStream;
		private BufferedReader sseReader;

		private Session(
				McpTarget target,
				RestClient rest,
				URI postUrl,
				Handshake handshake,
				String sessionId,
				InputStream sseStream,
				BufferedReader sseReader) {
			this.target = target;
			this.rest = rest;
			this.postUrl = postUrl;
			this.handshake = handshake;
			this.sessionId = sessionId;
			this.sseStream = sseStream;
			this.sseReader = sseReader;
		}

		static Session open(McpTarget target) {
			validateUrl(target.url());
			RestClient rest = restClient(target.timeoutMs());
			URI base = URI.create(target.url().trim());
			InputStream sseStream = null;
			BufferedReader sseReader = null;
			URI postUrl = base;
			if ("SSE".equals(target.transport())) {
				SseOpen opened = openSse(target);
				sseStream = opened.stream();
				sseReader = opened.reader();
				postUrl = opened.postUrl();
			}
			try {
				InitResult first = initialize(rest, target, postUrl, null, PROTOCOL, sseReader);
				notifyInitialized(rest, target, postUrl, first.sessionId());
				return session(target, rest, postUrl, first, sseStream, sseReader);
			}
			catch (RuntimeException ex) {
				if (shouldRetryProtocol(ex)) {
					try {
						InitResult retry = initialize(rest, target, postUrl, null, PROTOCOL_FALLBACK, sseReader);
						notifyInitialized(rest, target, postUrl, retry.sessionId());
						return session(target, rest, postUrl, retry, sseStream, sseReader);
					}
					catch (RuntimeException retryEx) {
						closeQuietly(sseReader, sseStream);
						throw retryEx;
					}
				}
				closeQuietly(sseReader, sseStream);
				throw ex;
			}
		}

		private static Session session(
				McpTarget target,
				RestClient rest,
				URI postUrl,
				InitResult init,
				InputStream sseStream,
				BufferedReader sseReader) {
			Session opened = new Session(target, rest, postUrl, init.handshake(), init.sessionId(), sseStream, sseReader);
			opened.nextId = 2;
			return opened;
		}

		private List<McpTool> listTools() {
			Map<String, Object> result = rpc("tools/list", Map.of());
			Object raw = result.get("tools");
			List<McpTool> tools = new ArrayList<>();
			if (!(raw instanceof List<?> items)) {
				return tools;
			}
			for (Object item : items) {
				if (!(item instanceof Map<?, ?> map)) {
					continue;
				}
				String name = string(map.get("name"));
				if (name.isBlank()) {
					continue;
				}
				String description = string(map.get("description"));
				if (description.isBlank()) {
					description = string(map.get("title"));
				}
				Map<String, Object> schema = schema(map.get("inputSchema"));
				tools.add(new McpTool(name, description, schema));
			}
			return tools;
		}

		private String callTool(String name, Map<String, Object> arguments) {
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("name", name);
			params.put("arguments", arguments);
			Map<String, Object> result = rpc("tools/call", params);
			boolean error = Boolean.TRUE.equals(result.get("isError"));
			String text = contentText(result.get("content"));
			if (error) {
				return "ERROR: " + (text.isBlank() ? "MCP 工具返回错误" : text);
			}
			return text.isBlank() ? McpJson.write(result) : text;
		}

		private Map<String, Object> rpc(String method, Object params) {
			int id = nextId++;
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("jsonrpc", "2.0");
			payload.put("id", id);
			payload.put("method", method);
			if (params != null) {
				payload.put("params", params);
			}
			Map<String, Object> response = post(rest, target, postUrl, sessionId, payload, id, sseReader, target.timeoutMs());
			Object error = response.get("error");
			if (error instanceof Map<?, ?> err) {
				throw new IllegalStateException(string(err.get("message")));
			}
			Object result = response.get("result");
			if (result instanceof Map<?, ?> map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> typed = (Map<String, Object>) map;
				return typed;
			}
			Map<String, Object> wrapper = new LinkedHashMap<>();
			wrapper.put("value", result);
			return wrapper;
		}

		@Override
		public void close() {
			try {
				if (sessionId != null && !"SSE".equals(target.transport())) {
					try {
						rest.delete()
								.uri(postUrl)
								.headers((headers) -> applyHeaders(headers, target, sessionId, false))
								.retrieve()
								.toBodilessEntity();
					}
					catch (RuntimeException ignored) {
						// session delete is optional
					}
				}
			}
			finally {
				closeQuietly(sseReader, sseStream);
			}
		}

		private static InitResult initialize(
				RestClient rest,
				McpTarget target,
				URI postUrl,
				String sessionId,
				String protocolVersion,
				BufferedReader sseReader) {
			Map<String, Object> clientInfo = new LinkedHashMap<>();
			clientInfo.put("name", "agent-platform-hub");
			clientInfo.put("version", "0.0.1");
			Map<String, Object> params = new LinkedHashMap<>();
			params.put("protocolVersion", protocolVersion);
			params.put("capabilities", Map.of("tools", Map.of()));
			params.put("clientInfo", clientInfo);
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("jsonrpc", "2.0");
			payload.put("id", 1);
			payload.put("method", "initialize");
			payload.put("params", params);
			RpcHttp http = exchange(rest, target, postUrl, sessionId, payload);
			String nextSession = http.sessionId() == null || http.sessionId().isBlank() ? sessionId : http.sessionId();
			Map<String, Object> response = decode(http, 1, sseReader, target.timeoutMs());
			Object error = response.get("error");
			if (error instanceof Map<?, ?> err) {
				throw new IllegalStateException(string(err.get("message")));
			}
			Map<String, Object> result = asObject(response.get("result"));
			Map<String, Object> serverInfo = asObject(result.get("serverInfo"));
			String negotiated = string(result.get("protocolVersion"));
			if (negotiated.isBlank()) {
				negotiated = protocolVersion;
			}
			String serverName = string(serverInfo.get("name"));
			String serverVersion = string(serverInfo.get("version"));
			return new InitResult(new Handshake(negotiated, serverName, serverVersion), nextSession);
		}

		private static void notifyInitialized(RestClient rest, McpTarget target, URI postUrl, String sessionId) {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("jsonrpc", "2.0");
			payload.put("method", "notifications/initialized");
			try {
				exchange(rest, target, postUrl, sessionId, payload);
			}
			catch (RuntimeException ignored) {
				// some servers omit a body for notifications
			}
		}

		private static Map<String, Object> post(
				RestClient rest,
				McpTarget target,
				URI postUrl,
				String sessionId,
				Map<String, Object> payload,
				int id,
				BufferedReader sseReader,
				int timeoutMs) {
			RpcHttp http = exchange(rest, target, postUrl, sessionId, payload);
			return decode(http, id, sseReader, timeoutMs);
		}

		private static RpcHttp exchange(
				RestClient rest,
				McpTarget target,
				URI postUrl,
				String sessionId,
				Map<String, Object> payload) {
			String json = McpJson.write(payload);
			return rest.post()
					.uri(postUrl)
					.headers((headers) -> applyHeaders(headers, target, sessionId, true))
					.contentType(MediaType.APPLICATION_JSON)
					.body(json)
					.exchange((req, res) -> {
						String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
						int status = res.getStatusCode().value();
						String contentType = res.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
						String nextSession = firstHeader(res.getHeaders(), "Mcp-Session-Id", "mcp-session-id");
						if (status >= 400) {
							throw new IllegalStateException("HTTP " + status + (body.isBlank() ? "" : " " + clip(body)));
						}
						return new RpcHttp(status, contentType, nextSession, body);
					});
		}

		private static Map<String, Object> decode(RpcHttp http, int id, BufferedReader sseReader, int timeoutMs) {
			String body = http.body() == null ? "" : http.body().trim();
			if (McpSse.looksLikeSse(http.contentType(), body) && !body.isBlank()) {
				Map<String, Object> payload = McpSse.jsonRpc(McpSse.parse(body), id);
				if (payload != null) {
					return payload;
				}
			}
			if (body.startsWith("{")) {
				return McpJson.object(body);
			}
			if (sseReader != null && (body.isBlank() || http.status() == 202)) {
				return waitSse(sseReader, id, timeoutMs);
			}
			if (body.isBlank() || http.status() == 202) {
				throw new IllegalStateException("MCP 响应为空");
			}
			throw new IllegalStateException("MCP 响应不是 JSON: " + clip(body));
		}

		private static Map<String, Object> waitSse(BufferedReader reader, int id, int timeoutMs) {
			long deadline = System.currentTimeMillis() + Math.max(1000, timeoutMs);
			while (System.currentTimeMillis() < deadline) {
				try {
					McpSse.Event event = McpSse.readEvent(reader);
					if (event == null) {
						break;
					}
					if (event.data() == null || event.data().isBlank() || !event.data().trim().startsWith("{")) {
						continue;
					}
					Map<String, Object> payload = McpJson.object(event.data().trim());
					if (McpJson.idKey(id).equals(McpJson.idKey(payload.get("id")))) {
						return payload;
					}
				}
				catch (java.io.IOException ex) {
					throw new IllegalStateException(ex.getMessage() == null ? "读取 MCP SSE 失败" : ex.getMessage(), ex);
				}
			}
			throw new IllegalStateException("SSE 超时未收到 JSON-RPC 响应");
		}

		private static SseOpen openSse(McpTarget target) {
			try {
				HttpClient httpClient = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(15))
						.followRedirects(HttpClient.Redirect.NEVER)
						.build();
				HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target.url().trim()))
						.GET()
						.timeout(Duration.ofMillis(target.timeoutMs()))
						.header("Accept", "text/event-stream")
						.header("MCP-Protocol-Version", PROTOCOL);
				applyAuth(builder, target);
				HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
				int status = response.statusCode();
				if (status >= 400) {
					String body = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
					throw new IllegalStateException("HTTP " + status + (body.isBlank() ? "" : " " + clip(body)));
				}
				InputStream stream = response.body();
				BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
				String endpoint = "";
				long deadline = System.currentTimeMillis() + target.timeoutMs();
				while (System.currentTimeMillis() < deadline) {
					McpSse.Event event = McpSse.readEvent(reader);
					if (event == null) {
						break;
					}
					if ("endpoint".equalsIgnoreCase(event.event()) && event.data() != null && !event.data().isBlank()) {
						endpoint = event.data().trim();
						break;
					}
				}
				if (endpoint.isBlank()) {
					closeQuietly(reader, stream);
					throw new IllegalStateException("SSE 未返回 endpoint 事件");
				}
				URI postUrl = URI.create(target.url().trim()).resolve(endpoint);
				return new SseOpen(postUrl, stream, reader);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("连接 MCP SSE 被中断");
			}
			catch (java.io.IOException ex) {
				throw new IllegalStateException(ex.getMessage() == null ? "连接 MCP SSE 失败" : ex.getMessage(), ex);
			}
		}

		private static RestClient restClient(int timeoutMs) {
			HttpClient httpClient = HttpClient.newBuilder()
					.connectTimeout(Duration.ofSeconds(15))
					.followRedirects(HttpClient.Redirect.NEVER)
					.build();
			JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
			factory.setReadTimeout(Duration.ofMillis(timeoutMs));
			return RestClient.builder().requestFactory(factory).build();
		}

		private static void applyHeaders(HttpHeaders headers, McpTarget target, String sessionId, boolean acceptSse) {
			headers.set("MCP-Protocol-Version", PROTOCOL);
			if (acceptSse) {
				headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
			}
			if (sessionId != null && !sessionId.isBlank()) {
				headers.set("Mcp-Session-Id", sessionId);
			}
			applyAuth(headers, target);
		}

		private static void applyAuth(HttpHeaders headers, McpTarget target) {
			String secret = target.secret() == null ? "" : target.secret().trim();
			if (secret.isBlank() || "NONE".equals(target.authType())) {
				return;
			}
			if ("BEARER".equals(target.authType())) {
				headers.setBearerAuth(secret);
				return;
			}
			if ("HEADER".equals(target.authType())) {
				String name = target.authHeader() == null || target.authHeader().isBlank() ? "Authorization" : target.authHeader();
				headers.set(name, secret);
			}
		}

		private static void applyAuth(HttpRequest.Builder builder, McpTarget target) {
			String secret = target.secret() == null ? "" : target.secret().trim();
			if (secret.isBlank() || "NONE".equals(target.authType())) {
				return;
			}
			if ("BEARER".equals(target.authType())) {
				builder.header("Authorization", "Bearer " + secret);
				return;
			}
			if ("HEADER".equals(target.authType())) {
				String name = target.authHeader() == null || target.authHeader().isBlank() ? "Authorization" : target.authHeader();
				builder.header(name, secret);
			}
		}

		private static String firstHeader(HttpHeaders headers, String... names) {
			for (String name : names) {
				String value = headers.getFirst(name);
				if (value != null && !value.isBlank()) {
					return value.trim();
				}
			}
			return "";
		}

		private static void validateUrl(String url) {
			if (url == null || url.isBlank()) {
				throw new IllegalArgumentException("MCP 地址不能为空");
			}
			String trimmed = url.trim();
			if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
				throw new IllegalArgumentException("只支持 http/https 的 MCP 地址");
			}
		}

		private static boolean shouldRetryProtocol(RuntimeException ex) {
			String text = message(ex).toLowerCase();
			return text.contains("protocol") || text.contains("version");
		}

		@SuppressWarnings("unchecked")
		private static Map<String, Object> asObject(Object value) {
			if (value instanceof Map<?, ?> map) {
				return (Map<String, Object>) map;
			}
			return new LinkedHashMap<>();
		}

		@SuppressWarnings("unchecked")
		private static Map<String, Object> schema(Object value) {
			if (value instanceof Map<?, ?> map) {
				Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
				if (!copy.containsKey("type")) {
					copy.put("type", "object");
				}
				if (!copy.containsKey("properties")) {
					copy.put("properties", new LinkedHashMap<>());
				}
				return copy;
			}
			Map<String, Object> empty = new LinkedHashMap<>();
			empty.put("type", "object");
			empty.put("properties", new LinkedHashMap<>());
			return empty;
		}

		private static String contentText(Object content) {
			if (content instanceof String text) {
				return clip(text);
			}
			if (!(content instanceof List<?> items) || items.isEmpty()) {
				return "";
			}
			StringBuilder text = new StringBuilder();
			for (Object item : items) {
				if (!(item instanceof Map<?, ?> map)) {
					continue;
				}
				String type = string(map.get("type"));
				if ("text".equals(type) || type.isBlank()) {
					String piece = string(map.get("text"));
					if (!piece.isBlank()) {
						if (text.length() > 0) {
							text.append('\n');
						}
						text.append(piece);
					}
				}
				else {
					if (text.length() > 0) {
						text.append('\n');
					}
					text.append('[').append(type).append(']');
				}
			}
			return clip(text.toString());
		}

		private static String string(Object value) {
			return value == null ? "" : String.valueOf(value);
		}

		private static String clip(String body) {
			if (body == null || body.isBlank()) {
				return "";
			}
			if (body.length() <= MAX_BODY) {
				return body;
			}
			return body.substring(0, MAX_BODY) + "\n...[truncated]";
		}

		private static void closeQuietly(BufferedReader reader, InputStream stream) {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (Exception ignored) {
				}
			}
			if (stream != null) {
				try {
					stream.close();
				}
				catch (Exception ignored) {
				}
			}
		}

		private record InitResult(Handshake handshake, String sessionId) {
		}

		private record RpcHttp(int status, String contentType, String sessionId, String body) {
		}

		private record SseOpen(URI postUrl, InputStream stream, BufferedReader reader) {
		}
	}

}
