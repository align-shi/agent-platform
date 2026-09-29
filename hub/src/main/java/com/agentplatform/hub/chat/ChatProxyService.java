package com.agentplatform.hub.chat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.agentplatform.hub.provider.ProviderEntity;

@Service
public class ChatProxyService {

	private final RestClient restClient;

	public ChatProxyService() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
		factory.setReadTimeout(Duration.ofMinutes(5));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	public ChatRound stream(
			ProviderEntity provider,
			String apiKey,
			String model,
			List<ChatDtos.Message> messages,
			ChatSink sink) throws Exception {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("model", model);
		payload.put("messages", messages);
		payload.put("stream", true);
		payload.put("stream_options", Map.of("include_usage", true));

		StringBuilder assistant = new StringBuilder();
		TokenUsage[] usage = { TokenUsage.NONE };
		restClient.post()
				.uri(ChatCompletionsPaths.resolve(provider.getBaseUrl()))
				.header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.exchange((req, res) -> {
					if (res.getStatusCode().isError()) {
						String error = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
						throw new IllegalStateException(error.isBlank() ? "upstream " + res.getStatusCode() : error);
					}
					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
						String line;
						while ((line = reader.readLine()) != null) {
							if (line.isBlank()) {
								continue;
							}
							if (line.startsWith("data:")) {
								String data = line.substring(5).trim();
								if ("[DONE]".equals(data)) {
									break;
								}
								TokenUsage found = TokenUsage.fromSseData(data);
								if (!found.isEmpty()) {
									usage[0] = found;
								}
								assistant.append(extractDelta(data));
								sink.delta(data);
							}
						}
					}
					return null;
				});
		return new ChatRound(assistant.toString(), List.of(), usage[0]);
	}

	public ChatRound streamRound(
			ProviderEntity provider,
			String apiKey,
			String model,
			List<Map<String, Object>> messages,
			List<Map<String, Object>> tools,
			ChatSink sink) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("model", model);
		payload.put("messages", messages);
		payload.put("stream", true);
		payload.put("stream_options", Map.of("include_usage", true));
		if (tools != null && !tools.isEmpty()) {
			payload.put("tools", tools);
			payload.put("tool_choice", "auto");
		}
		StreamToolAssembler assembler = new StreamToolAssembler();
		restClient.post()
				.uri(ChatCompletionsPaths.resolve(provider.getBaseUrl()))
				.header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.exchange((req, res) -> {
					if (res.getStatusCode().isError()) {
						String error = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
						throw new IllegalStateException(error.isBlank() ? "upstream " + res.getStatusCode() : error);
					}
					try (BufferedReader reader = new BufferedReader(
							new InputStreamReader(res.getBody(), StandardCharsets.UTF_8))) {
						String line;
						while ((line = reader.readLine()) != null) {
							if (line.isBlank() || !line.startsWith("data:")) {
								continue;
							}
							String data = line.substring(5).trim();
							if (data.isEmpty() || "[DONE]".equals(data)) {
								if ("[DONE]".equals(data)) {
									break;
								}
								continue;
							}
							String fragment = assembler.accept(data);
							if (!fragment.isEmpty()) {
								sink.delta(data);
							}
						}
					}
					return null;
				});
		return assembler.toRound();
	}

	public ChatRound complete(
			ProviderEntity provider,
			String apiKey,
			String model,
			List<Map<String, Object>> messages,
			List<Map<String, Object>> tools) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("model", model);
		payload.put("messages", messages);
		payload.put("stream", false);
		if (tools != null && !tools.isEmpty()) {
			payload.put("tools", tools);
			payload.put("tool_choice", "auto");
		}

		Map<String, Object> body = restClient.post()
				.uri(ChatCompletionsPaths.resolve(provider.getBaseUrl()))
				.header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.retrieve()
				.onStatus(HttpStatusCode::isError, (req, res) -> {
					String error = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
					throw new IllegalStateException(error.isBlank() ? "upstream " + res.getStatusCode() : error);
				})
				.body(new ParameterizedTypeReference<Map<String, Object>>() { });
		if (body == null) {
			throw new IllegalStateException("upstream returned empty body");
		}
		Object choices = body.get("choices");
		if (!(choices instanceof List<?> list) || list.isEmpty() || !(list.get(0) instanceof Map<?, ?> choice)) {
			throw new IllegalStateException("upstream returned no choices");
		}
		Object message = choice.get("message");
		if (!(message instanceof Map<?, ?> msg)) {
			throw new IllegalStateException("upstream returned no message");
		}
		ChatRound round = ChatRound.fromMessage(msg);
		return new ChatRound(round.content(), round.toolCalls(), TokenUsage.from(body.get("usage")));
	}

	public String completeText(ProviderEntity provider, String apiKey, String model, String system, String user) {
		List<Map<String, Object>> messages = new ArrayList<>();
		messages.add(Map.of("role", "system", "content", system));
		messages.add(Map.of("role", "user", "content", user));
		ChatRound round = complete(provider, apiKey, model, messages, List.of());
		return round.content() == null ? "" : round.content().trim();
	}

	static String deltaPayload(String content) {
		return "{\"choices\":[{\"delta\":{\"content\":" + jsonString(content) + "}}]}";
	}

	static String jsonString(String value) {
		if (value == null) {
			return "\"\"";
		}
		StringBuilder out = new StringBuilder("\"");
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> out.append("\\\\");
				case '"' -> out.append("\\\"");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 32) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
		return out.toString();
	}

	static String extractDelta(String json) {
		String marker = "\"content\":";
		int from = json.indexOf(marker);
		if (from < 0) {
			return "";
		}
		int i = from + marker.length();
		while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
			i++;
		}
		if (i >= json.length() || json.charAt(i) != '"') {
			return "";
		}
		i++;
		StringBuilder text = new StringBuilder();
		while (i < json.length()) {
			char c = json.charAt(i++);
			if (c == '\\' && i < json.length()) {
				char next = json.charAt(i++);
				switch (next) {
					case 'n' -> text.append('\n');
					case 't' -> text.append('\t');
					case 'r' -> text.append('\r');
					case '"' -> text.append('"');
					case '\\' -> text.append('\\');
					default -> text.append(next);
				}
			}
			else if (c == '"') {
				break;
			}
			else {
				text.append(c);
			}
		}
		return text.toString();
	}

}
