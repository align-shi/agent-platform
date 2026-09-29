package com.agentplatform.hub.knowledge;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.agentplatform.hub.provider.ProviderEntity;

@Service
public class EmbeddingClient {

	private static final int BATCH = 10;

	private final RestClient restClient;

	public EmbeddingClient() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
		factory.setReadTimeout(Duration.ofMinutes(2));
		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	public List<double[]> embed(ProviderEntity provider, String apiKey, List<String> inputs) {
		if (inputs == null || inputs.isEmpty()) {
			return List.of();
		}
		List<double[]> vectors = new ArrayList<>();
		for (int start = 0; start < inputs.size(); start += BATCH) {
			List<String> batch = inputs.subList(start, Math.min(start + BATCH, inputs.size()));
			vectors.addAll(embedBatch(provider, apiKey, batch));
		}
		return vectors;
	}

	private List<double[]> embedBatch(ProviderEntity provider, String apiKey, List<String> inputs) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("model", provider.getDefaultModel());
		payload.put("input", inputs);
		if (provider.getDimensions() != null) {
			payload.put("dimensions", provider.getDimensions());
		}
		String body = restClient.post()
				.uri(EmbeddingPaths.resolve(provider.getBaseUrl()))
				.header("Authorization", "Bearer " + apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(payload)
				.exchange((request, response) -> {
					String text = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
					if (response.getStatusCode().isError()) {
						throw new IllegalStateException(clip(text.isBlank() ? "upstream " + response.getStatusCode() : text));
					}
					return text;
				});
		List<double[]> vectors = EmbeddingVectors.parseResponse(body);
		if (vectors.size() != inputs.size()) {
			throw new IllegalStateException("Embedding 接口返回的向量数量和文本不一致");
		}
		return vectors;
	}

	private static String clip(String text) {
		String value = text.replaceAll("\\s+", " ").trim();
		if (value.length() <= 300) {
			return value;
		}
		return value.substring(0, 300);
	}

}
