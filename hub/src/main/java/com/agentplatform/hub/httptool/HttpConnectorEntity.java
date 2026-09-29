package com.agentplatform.hub.httptool;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "http_connectors")
public class HttpConnectorEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(columnDefinition = "TEXT")
	private String description;

	@Column(nullable = false, length = 32)
	private String protocol = "DECLARATIVE";

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

	@Column(name = "forward_credentials", nullable = false)
	private boolean forwardCredentials = false;

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

	public String getProtocol() {
		return protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
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

	public boolean isForwardCredentials() {
		return forwardCredentials;
	}

	public void setForwardCredentials(boolean forwardCredentials) {
		this.forwardCredentials = forwardCredentials;
	}

}
