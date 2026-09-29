package com.agentplatform.hub.httptool;

import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(20)
public class HttpConnectorMigrator implements ApplicationRunner {

	private final HttpConnectorRepository connectors;
	private final HttpToolRepository tools;
	private final AgentHttpToolRepository bindings;

	public HttpConnectorMigrator(
			HttpConnectorRepository connectors,
			HttpToolRepository tools,
			AgentHttpToolRepository bindings) {
		this.connectors = connectors;
		this.tools = tools;
		this.bindings = bindings;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		for (HttpToolEntity tool : tools.findAll()) {
			if (tool.getConnectorId() != null && !tool.getConnectorId().isBlank()) {
				continue;
			}
			HttpConnectorEntity connector = new HttpConnectorEntity();
			connector.setId(UUID.randomUUID().toString());
			connector.setName(tool.getName());
			connector.setDescription(tool.getDescription() == null ? "" : tool.getDescription());
			connector.setProtocol("DECLARATIVE");
			connector.setAuthType(tool.getAuthType() == null || tool.getAuthType().isBlank() ? "NONE" : tool.getAuthType());
			connector.setAuthHeader(tool.getAuthHeader() == null || tool.getAuthHeader().isBlank() ? "Authorization" : tool.getAuthHeader());
			connector.setSecretCipher(tool.getSecretCipher());
			connector.setKeyLast4(tool.getKeyLast4());
			connector.setTimeoutMs(tool.getTimeoutMs() == null ? 15000 : tool.getTimeoutMs());
			connector.setEnabled(tool.isEnabled());
			connector.setForwardCredentials(false);
			connectors.save(connector);
			tool.setConnectorId(connector.getId());
			tool.setSortOrder(0);
			tools.save(tool);
		}
		for (AgentHttpToolEntity binding : bindings.findAll()) {
			if (connectors.existsById(binding.getHttpToolId())) {
				continue;
			}
			tools.findById(binding.getHttpToolId()).ifPresent((tool) -> {
				if (tool.getConnectorId() != null && !tool.getConnectorId().isBlank()) {
					binding.setHttpToolId(tool.getConnectorId());
					bindings.save(binding);
				}
			});
		}
	}

}
