package com.agentplatform.hub.httptool;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "http_tools")
public class HttpToolEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "connector_id", length = 36)
	private String connectorId;

	@Column(name = "sort_order")
	private int sortOrder;

	@Column(nullable = false)
	private String name;

	@Column(name = "tool_name", nullable = false, length = 64, unique = true)
	private String toolName;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false, length = 16)
	private String method;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String url;

	@Column(name = "auth_type", nullable = false, length = 16)
	private String authType = "NONE";

	@Column(name = "auth_header", length = 64)
	private String authHeader;

	@Column(name = "secret_cipher", columnDefinition = "TEXT")
	private String secretCipher;

	@Column(name = "key_last4", length = 8)
	private String keyLast4;

	@Column(name = "timeout_ms")
	private Integer timeoutMs;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "parameters_json", columnDefinition = "TEXT")
	private String parametersJson;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	@Transient
	public List<HttpToolParam> parameters() {
		return JsonLite.paramsFromJson(parametersJson);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getConnectorId() {
		return connectorId;
	}

	public void setConnectorId(String connectorId) {
		this.connectorId = connectorId;
	}

	public int getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getToolName() {
		return toolName;
	}

	public void setToolName(String toolName) {
		this.toolName = toolName;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getAuthType() {
		return authType;
	}

	public void setAuthType(String authType) {
		this.authType = authType;
	}

	public String getAuthHeader() {
		return authHeader;
	}

	public void setAuthHeader(String authHeader) {
		this.authHeader = authHeader;
	}

	public String getSecretCipher() {
		return secretCipher;
	}

	public void setSecretCipher(String secretCipher) {
		this.secretCipher = secretCipher;
	}

	public String getKeyLast4() {
		return keyLast4;
	}

	public void setKeyLast4(String keyLast4) {
		this.keyLast4 = keyLast4;
	}

	public Integer getTimeoutMs() {
		return timeoutMs;
	}

	public void setTimeoutMs(Integer timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getParametersJson() {
		return parametersJson;
	}

	public void setParametersJson(String parametersJson) {
		this.parametersJson = parametersJson;
	}

}
