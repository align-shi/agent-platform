package com.agentplatform.hub.feishu;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "feishu_bots")
public class FeishuBotEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "app_id", nullable = false, unique = true, length = 64)
	private String appId;

	@Column(name = "app_secret_cipher", nullable = false, length = 2048)
	private String appSecretCipher;

	@Column(name = "secret_last4", length = 8)
	private String secretLast4;

	@Column(name = "agent_id", nullable = false, length = 36)
	private String agentId;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "last_error", columnDefinition = "TEXT")
	private String lastError;

	@Column(name = "last_event_at")
	private Instant lastEventAt;

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

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getAppSecretCipher() {
		return appSecretCipher;
	}

	public void setAppSecretCipher(String appSecretCipher) {
		this.appSecretCipher = appSecretCipher;
	}

	public String getSecretLast4() {
		return secretLast4;
	}

	public void setSecretLast4(String secretLast4) {
		this.secretLast4 = secretLast4;
	}

	public String getAgentId() {
		return agentId;
	}

	public void setAgentId(String agentId) {
		this.agentId = agentId;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public Instant getLastEventAt() {
		return lastEventAt;
	}

	public void setLastEventAt(Instant lastEventAt) {
		this.lastEventAt = lastEventAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
