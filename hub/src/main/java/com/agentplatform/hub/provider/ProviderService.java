package com.agentplatform.hub.provider;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.crypto.SecretCipher;
import com.agentplatform.hub.knowledge.KnowledgeBaseRepository;

@Service
public class ProviderService {

	private final ProviderRepository repository;
	private final SecretCipher cipher;
	private final KnowledgeBaseRepository knowledgeBases;

	public ProviderService(ProviderRepository repository, SecretCipher cipher, KnowledgeBaseRepository knowledgeBases) {
		this.repository = repository;
		this.cipher = cipher;
		this.knowledgeBases = knowledgeBases;
	}

	@Transactional(readOnly = true)
	public List<ProviderDtos.View> list() {
		return repository.findAll().stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public List<ProviderEntity> usableChatProviders() {
		return repository.findAll().stream()
				.filter((entity) -> entity.getType() == ProviderType.CHAT)
				.filter((entity) -> entity.getDefaultModel() != null && !entity.getDefaultModel().isBlank())
				.filter((entity) -> entity.getBaseUrl() != null && !entity.getBaseUrl().isBlank())
				.filter((entity) -> entity.getApiKeyCipher() != null && !entity.getApiKeyCipher().isBlank())
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ProviderEntity> usableEmbeddingProviders() {
		return repository.findAll().stream()
				.filter((entity) -> entity.getType() == ProviderType.EMBEDDING)
				.filter((entity) -> entity.getDefaultModel() != null && !entity.getDefaultModel().isBlank())
				.filter((entity) -> entity.getBaseUrl() != null && !entity.getBaseUrl().isBlank())
				.filter((entity) -> entity.getApiKeyCipher() != null && !entity.getApiKeyCipher().isBlank())
				.toList();
	}

	@Transactional(readOnly = true)
	public ProviderEntity require(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
	}

	@Transactional(readOnly = true)
	public String decryptApiKey(ProviderEntity entity) {
		return cipher.decrypt(entity.getApiKeyCipher());
	}

	@Transactional
	public ProviderDtos.View create(ProviderDtos.UpsertRequest request) {
		if (request.apiKey() == null || request.apiKey().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey is required");
		}
		ProviderEntity entity = new ProviderEntity();
		entity.setId(UUID.randomUUID().toString());
		apply(entity, request, true);
		return toView(repository.save(entity));
	}

	@Transactional
	public ProviderDtos.View update(String id, ProviderDtos.UpsertRequest request) {
		ProviderEntity entity = require(id);
		apply(entity, request, false);
		return toView(repository.save(entity));
	}

	@Transactional
	public void delete(String id) {
		if (!repository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found");
		}
		if (knowledgeBases.existsByEmbeddingProviderId(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仍有知识库使用这个 Embedding 模型，请先更换或删除知识库");
		}
		repository.deleteById(id);
	}

	private void apply(ProviderEntity entity, ProviderDtos.UpsertRequest request, boolean creating) {
		entity.setName(request.name().trim());
		entity.setBaseUrl(trimSlash(request.baseUrl().trim()));
		entity.setType(request.type());
		entity.setVendor(blankToNull(request.vendor()));
		entity.setDefaultModel(blankToNull(request.defaultModel()));
		entity.setDimensions(request.type() == ProviderType.EMBEDDING ? normalizeDimensions(request.dimensions()) : null);
		if (request.apiKey() != null && !request.apiKey().isBlank()) {
			String key = request.apiKey().trim();
			entity.setApiKeyCipher(cipher.encrypt(key));
			entity.setKeyLast4(last4(key));
		}
		else if (creating) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "apiKey is required");
		}
	}

	private ProviderDtos.View toView(ProviderEntity entity) {
		return new ProviderDtos.View(
				entity.getId(),
				entity.getName(),
				entity.getBaseUrl(),
				entity.getType(),
				entity.getVendor(),
				entity.getDefaultModel(),
				entity.getDimensions(),
				entity.getKeyLast4(),
				true);
	}

	static String trimSlash(String url) {
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}

	static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	static Integer normalizeDimensions(Integer dimensions) {
		if (dimensions == null) {
			return null;
		}
		if (dimensions < 32 || dimensions > 4096) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "向量维度须在 32 到 4096 之间");
		}
		return dimensions;
	}

	static String last4(String key) {
		if (key.length() <= 4) {
			return key;
		}
		return key.substring(key.length() - 4);
	}

}
