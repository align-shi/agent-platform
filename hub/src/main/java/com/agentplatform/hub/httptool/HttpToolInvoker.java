package com.agentplatform.hub.httptool;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpToolInvoker {

	private static final int MAX_BODY = 100000;

	public String invoke(HttpToolEntity tool, String argumentsJson, String secret, HttpConnectorEntity connector) {
		Map<String, String> args = new LinkedHashMap<>(JsonLite.object(argumentsJson));
		HttpToolRequest request = HttpToolRequest.build(tool, args);
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(15))
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
		int timeout = connector.getTimeoutMs() == null ? 15000 : Math.min(60000, Math.max(1000, connector.getTimeoutMs()));
		factory.setReadTimeout(Duration.ofMillis(timeout));
		RestClient client = RestClient.builder().requestFactory(factory).build();
		String trimmedSecret = secret == null ? "" : secret.trim();
		try {
			RestClient.RequestBodySpec spec = client.method(HttpMethod.valueOf(request.method()))
					.uri(request.uri())
					.headers((headers) -> {
						request.headers().forEach(headers::set);
						applyAuth(headers, connector, trimmedSecret);
					});
			if (request.body() != null) {
				spec.contentType(MediaType.APPLICATION_JSON).body(request.body());
			}
			return spec.exchange((req, res) -> {
				String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
				return preface(request, connector, trimmedSecret)
						+ "HTTP " + res.getStatusCode().value() + "\n"
						+ clip(body);
			});
		}
		catch (RuntimeException ex) {
			return preface(request, connector, trimmedSecret)
					+ "ERROR: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
		}
	}

	private static String preface(HttpToolRequest request, HttpConnectorEntity connector, String secret) {
		return request.method() + " " + request.uri() + "\n" + authSummary(connector, secret) + "\n\n";
	}

	private static String authSummary(HttpConnectorEntity connector, String secret) {
		String type = connector.getAuthType() == null ? "NONE" : connector.getAuthType();
		if ("NONE".equals(type)) {
			return "鉴权: 无";
		}
		if (secret == null || secret.isBlank()) {
			return "鉴权: 未携带密钥";
		}
		if ("BEARER".equals(type)) {
			return "鉴权: 已携带 Authorization Bearer（" + secret.length() + " 字符）";
		}
		String name = connector.getAuthHeader() == null || connector.getAuthHeader().isBlank() ? "Authorization" : connector.getAuthHeader();
		return "鉴权: 已携带请求头 " + name + "（" + secret.length() + " 字符）";
	}

	private static void applyAuth(HttpHeaders headers, HttpConnectorEntity connector, String secret) {
		if (secret == null || secret.isBlank() || "NONE".equals(connector.getAuthType())) {
			return;
		}
		if ("BEARER".equals(connector.getAuthType())) {
			headers.setBearerAuth(secret);
			return;
		}
		if ("HEADER".equals(connector.getAuthType())) {
			String name = connector.getAuthHeader() == null || connector.getAuthHeader().isBlank() ? "Authorization" : connector.getAuthHeader();
			headers.set(name, secret);
		}
	}

	private static String clip(String body) {
		if (body == null || body.isBlank()) {
			return "(empty)";
		}
		if (body.length() <= MAX_BODY) {
			return body;
		}
		return body.substring(0, MAX_BODY) + "\n...[truncated]";
	}

}
