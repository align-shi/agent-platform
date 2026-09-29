package com.agentplatform.hub.mcp;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "mcp_servers")
public class RemoteMcpEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String url;

	@Column(nullable = false, length = 16)
	private String transport = "STREAMABLE";

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

	@Column(name = "tools_json", columnDefinition = "TEXT")
	private String toolsJson;

	@Column(name = "server_name")
	private String serverName;

	@Column(name = "server_version")
	private String serverVersion;

	@Column(name = "protocol_version", length = 32)
	private String protocolVersion;

	@Column(name = "last_error", columnDefinition = "TEXT")
	private String lastError;

	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	void prepareInsert() {
		Instant now = Instant.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getTransport() {
		return transport;
	}

	public void setTransport(String transport) {
		this.transport = transport;
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

	public String getToolsJson() {
		return toolsJson;
	}

	public void setToolsJson(String toolsJson) {
		this.toolsJson = toolsJson;
	}

	public String getServerName() {
		return serverName;
	}

	public void setServerName(String serverName) {
		this.serverName = serverName;
	}

	public String getServerVersion() {
		return serverVersion;
	}

	public void setServerVersion(String serverVersion) {
		this.serverVersion = serverVersion;
	}

	public String getProtocolVersion() {
		return protocolVersion;
	}

	public void setProtocolVersion(String protocolVersion) {
		this.protocolVersion = protocolVersion;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public Instant getLastSyncedAt() {
		return lastSyncedAt;
	}

	public void setLastSyncedAt(Instant lastSyncedAt) {
		this.lastSyncedAt = lastSyncedAt;
	}

}
