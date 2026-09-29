package com.agentplatform.hub.provider;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "providers")
public class ProviderEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(nullable = false)
	private String name;

	@Column(name = "base_url", nullable = false)
	private String baseUrl;

	@Column(length = 32)
	private String vendor;

	@Column(name = "default_model")
	private String defaultModel;

	@Column(name = "api_key_cipher", nullable = false, length = 2048)
	private String apiKeyCipher;

	@Column(name = "key_last4", nullable = false, length = 8)
	private String keyLast4;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private ProviderType type;

	private Integer dimensions;

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

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getVendor() {
		return vendor;
	}

	public void setVendor(String vendor) {
		this.vendor = vendor;
	}

	public String getDefaultModel() {
		return defaultModel;
	}

	public void setDefaultModel(String defaultModel) {
		this.defaultModel = defaultModel;
	}

	public String getApiKeyCipher() {
		return apiKeyCipher;
	}

	public void setApiKeyCipher(String apiKeyCipher) {
		this.apiKeyCipher = apiKeyCipher;
	}

	public String getKeyLast4() {
		return keyLast4;
	}

	public void setKeyLast4(String keyLast4) {
		this.keyLast4 = keyLast4;
	}

	public ProviderType getType() {
		return type;
	}

	public void setType(ProviderType type) {
		this.type = type;
	}

	public Integer getDimensions() {
		return dimensions;
	}

	public void setDimensions(Integer dimensions) {
		this.dimensions = dimensions;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
