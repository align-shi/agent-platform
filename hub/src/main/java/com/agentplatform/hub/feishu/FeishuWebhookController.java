package com.agentplatform.hub.feishu;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/feishu/events")
public class FeishuWebhookController {

	private final FeishuInboundService inbound;

	public FeishuWebhookController(FeishuInboundService inbound) {
		this.inbound = inbound;
	}

	@PostMapping("/{botId}")
	public Map<String, Object> receive(@PathVariable String botId, HttpServletRequest request) throws IOException {
		String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		return inbound.accept(
				botId,
				body,
				request.getHeader("X-Lark-Request-Timestamp"),
				request.getHeader("X-Lark-Request-Nonce"),
				request.getHeader("X-Lark-Signature"));
	}

}
