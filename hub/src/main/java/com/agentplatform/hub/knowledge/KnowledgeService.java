package com.agentplatform.hub.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.provider.ProviderEntity;
import com.agentplatform.hub.provider.ProviderService;
import com.agentplatform.hub.provider.ProviderType;

@Service
public class KnowledgeService {

	static final int MAX_CHARS = 200_000;
	static final int MAX_CHUNKS = 200;
	static final double MIN_SCORE = 0.55;
	static final int MAX_SNIPPETS = 8;

	private final KnowledgeBaseRepository bases;
	private final KnowledgeDocumentRepository documents;
	private final KnowledgeChunkRepository chunks;
	private final AgentKnowledgeRepository agentKnowledge;
	private final ProviderService providers;
	private final EmbeddingClient embedding;

	public KnowledgeService(
			KnowledgeBaseRepository bases,
			KnowledgeDocumentRepository documents,
			KnowledgeChunkRepository chunks,
			AgentKnowledgeRepository agentKnowledge,
			ProviderService providers,
			EmbeddingClient embedding) {
		this.bases = bases;
		this.documents = documents;
		this.chunks = chunks;
		this.agentKnowledge = agentKnowledge;
		this.providers = providers;
		this.embedding = embedding;
	}

	@Transactional(readOnly = true)
	public List<KnowledgeDtos.View> list() {
		return bases.findAll().stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public KnowledgeBaseEntity require(String id) {
		return bases.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识库不存在"));
	}

	@Transactional(readOnly = true)
	public List<String> idsForAgent(String agentId) {
		return agentKnowledge.findByAgentIdOrderByKnowledgeBaseIdAsc(agentId).stream()
				.map(AgentKnowledgeEntity::getKnowledgeBaseId)
				.toList();
	}

	@Transactional
	public KnowledgeDtos.View create(KnowledgeDtos.UpsertRequest request) {
		KnowledgeBaseEntity entity = new KnowledgeBaseEntity();
		entity.setId(UUID.randomUUID().toString());
		apply(entity, request, false);
		return toView(bases.save(entity));
	}

	@Transactional
	public KnowledgeDtos.View update(String id, KnowledgeDtos.UpsertRequest request) {
		KnowledgeBaseEntity entity = require(id);
		boolean hasDocuments = documents.countByKnowledgeBaseId(id) > 0;
		apply(entity, request, hasDocuments);
		return toView(bases.save(entity));
	}

	@Transactional
	public void delete(String id) {
		require(id);
		chunks.deleteByKnowledgeBaseId(id);
		documents.deleteByKnowledgeBaseId(id);
		agentKnowledge.deleteByKnowledgeBaseId(id);
		bases.deleteById(id);
	}

	@Transactional
	public void replaceBindings(String agentId, List<String> knowledgeBaseIds) {
		agentKnowledge.deleteByAgentId(agentId);
		if (knowledgeBaseIds == null) {
			return;
		}
		List<AgentKnowledgeEntity> rows = new ArrayList<>();
		for (String knowledgeBaseId : knowledgeBaseIds) {
			if (knowledgeBaseId == null || knowledgeBaseId.isBlank()) {
				continue;
			}
			String normalized = knowledgeBaseId.trim();
			if (rows.stream().anyMatch((row) -> row.getKnowledgeBaseId().equals(normalized))) {
				continue;
			}
			require(normalized);
			AgentKnowledgeEntity row = new AgentKnowledgeEntity();
			row.setId(UUID.randomUUID().toString());
			row.setAgentId(agentId);
			row.setKnowledgeBaseId(normalized);
			rows.add(row);
		}
		agentKnowledge.saveAll(rows);
	}

	@Transactional
	public void deleteBindingsForAgent(String agentId) {
		agentKnowledge.deleteByAgentId(agentId);
	}

	@Transactional(readOnly = true)
	public List<KnowledgeDtos.DocumentView> listDocuments(String knowledgeBaseId) {
		require(knowledgeBaseId);
		return documents.findByKnowledgeBaseIdOrderByCreatedAtAsc(knowledgeBaseId).stream()
				.map(this::toDocument)
				.toList();
	}

	public KnowledgeDtos.DocumentView addDocument(String knowledgeBaseId, KnowledgeDtos.DocumentRequest request) {
		KnowledgeBaseEntity base = require(knowledgeBaseId);
		ProviderEntity provider = requireEmbedding(base.getEmbeddingProviderId());
		String text = request.content() == null ? "" : request.content().replace("\uFEFF", "").trim();
		if (text.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "正文不能为空");
		}
		if (text.length() > MAX_CHARS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "正文超过 20 万字，请拆分后再上传");
		}
		List<String> pieces = TextChunker.split(text, base.getChunkSize(), base.getChunkOverlap());
		if (pieces.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "正文不能为空");
		}
		if (pieces.size() > MAX_CHUNKS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文档切分后超过 200 段，请缩短正文或加大分块长度");
		}
		KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
		document.setId(UUID.randomUUID().toString());
		document.setKnowledgeBaseId(base.getId());
		document.setName(clipName(request.name()));
		document.setStatus("INDEXING");
		document.setCharCount(text.length());
		document.prepareInsert();
		document = documents.save(document);
		try {
			List<double[]> vectors = embedding.embed(provider, providers.decryptApiKey(provider), pieces);
			int dimension = vectors.get(0).length;
			if (base.getEmbeddingDim() != null && base.getEmbeddingDim() != dimension) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "向量维度与知识库已有文档不一致");
			}
			if (provider.getDimensions() != null && provider.getDimensions() != dimension) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "返回的向量维度和模型配置不一致");
			}
			List<KnowledgeChunkEntity> rows = new ArrayList<>();
			for (int i = 0; i < pieces.size(); i++) {
				KnowledgeChunkEntity chunk = new KnowledgeChunkEntity();
				chunk.setId(UUID.randomUUID().toString());
				chunk.setKnowledgeBaseId(base.getId());
				chunk.setDocumentId(document.getId());
				chunk.setChunkIndex(i);
				chunk.setContent(pieces.get(i));
				chunk.setEmbedding(EmbeddingVectors.encode(vectors.get(i)));
				rows.add(chunk);
			}
			chunks.saveAll(rows);
			if (base.getEmbeddingDim() == null) {
				base.setEmbeddingDim(dimension);
				bases.save(base);
			}
			document.setStatus("READY");
			document.setErrorMessage(null);
			return toDocument(documents.save(document));
		}
		catch (RuntimeException ex) {
			chunks.deleteByDocumentId(document.getId());
			document.setStatus("FAILED");
			document.setErrorMessage(clip(reason(ex)));
			documents.save(document);
			if (ex instanceof ResponseStatusException status) {
				throw status;
			}
			throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "向量化失败：" + clip(reason(ex)));
		}
	}

	@Transactional
	public void deleteDocument(String knowledgeBaseId, String documentId) {
		require(knowledgeBaseId);
		KnowledgeDocumentEntity document = documents.findById(documentId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
		if (!knowledgeBaseId.equals(document.getKnowledgeBaseId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在");
		}
		chunks.deleteByDocumentId(documentId);
		documents.delete(document);
		if (documents.countByKnowledgeBaseId(knowledgeBaseId) == 0) {
			KnowledgeBaseEntity base = require(knowledgeBaseId);
			base.setEmbeddingDim(null);
			bases.save(base);
		}
	}

	public Retrieval retrieve(String agentId, String query) {
		List<String> ids = idsForAgent(agentId);
		if (ids.isEmpty() || query == null || query.isBlank()) {
			return Retrieval.empty();
		}
		String probe = query.trim();
		if (probe.length() > 2000) {
			probe = probe.substring(0, 2000);
		}
		List<Snippet> snippets = new ArrayList<>();
		for (String id : ids) {
			KnowledgeBaseEntity base = bases.findById(id).orElse(null);
			if (base == null || base.getEmbeddingDim() == null) {
				continue;
			}
			List<KnowledgeChunkEntity> rows = chunks.findByKnowledgeBaseId(id);
			if (rows.isEmpty()) {
				continue;
			}
			ProviderEntity provider = requireEmbedding(base.getEmbeddingProviderId());
			double[] queryVector = embedding.embed(provider, providers.decryptApiKey(provider), List.of(probe)).get(0);
			Map<String, String> names = documentNames(id);
			List<Snippet> ranked = new ArrayList<>();
			for (KnowledgeChunkEntity row : rows) {
				double score = VectorMath.cosine(queryVector, EmbeddingVectors.decode(row.getEmbedding()));
				if (score < MIN_SCORE) {
					continue;
				}
				ranked.add(new Snippet(score, base.getName(), names.getOrDefault(row.getDocumentId(), "文档"), row.getContent()));
			}
			ranked.sort(Comparator.comparingDouble(Snippet::score).reversed());
			int limit = Math.max(1, base.getTopK());
			snippets.addAll(ranked.subList(0, Math.min(limit, ranked.size())));
		}
		snippets.sort(Comparator.comparingDouble(Snippet::score).reversed());
		if (snippets.size() > MAX_SNIPPETS) {
			snippets = snippets.subList(0, MAX_SNIPPETS);
		}
		if (snippets.isEmpty()) {
			return Retrieval.miss();
		}
		StringBuilder text = new StringBuilder("检索到的知识库资料：");
		List<Hit> hits = new ArrayList<>();
		for (Snippet snippet : snippets) {
			text.append("\n\n【").append(snippet.baseName()).append(" / ").append(snippet.documentName()).append("】\n");
			text.append(snippet.content().trim());
			hits.add(new Hit(snippet.baseName(), snippet.documentName(), excerpt(snippet.content())));
		}
		return new Retrieval(text.toString(), hits, true);
	}

	private void apply(KnowledgeBaseEntity entity, KnowledgeDtos.UpsertRequest request, boolean hasDocuments) {
		ProviderEntity provider = requireEmbedding(request.embeddingProviderId());
		if (hasDocuments && !provider.getId().equals(entity.getEmbeddingProviderId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "已有文档，请先删除文档后再更换 Embedding 模型");
		}
		int chunkSize = clamp(request.chunkSize(), 200, 2000, 800);
		int chunkOverlap = clamp(request.chunkOverlap(), 0, chunkSize / 2, Math.min(80, chunkSize / 2));
		entity.setName(request.name().trim());
		entity.setDescription(request.description() == null ? "" : request.description().trim());
		entity.setEmbeddingProviderId(provider.getId());
		entity.setTopK(clamp(request.topK(), 1, 10, 4));
		entity.setChunkSize(chunkSize);
		entity.setChunkOverlap(chunkOverlap);
	}

	private ProviderEntity requireEmbedding(String providerId) {
		ProviderEntity provider = providers.require(providerId);
		if (provider.getType() != ProviderType.EMBEDDING) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 Embedding 模型");
		}
		if (provider.getDefaultModel() == null || provider.getDefaultModel().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Embedding 模型还没有填写模型名");
		}
		if (provider.getBaseUrl() == null || provider.getBaseUrl().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Embedding 模型还没有填写地址");
		}
		return provider;
	}

	private Map<String, String> documentNames(String knowledgeBaseId) {
		Map<String, String> names = new HashMap<>();
		for (KnowledgeDocumentEntity document : documents.findByKnowledgeBaseIdOrderByCreatedAtAsc(knowledgeBaseId)) {
			names.put(document.getId(), document.getName());
		}
		return names;
	}

	private KnowledgeDtos.View toView(KnowledgeBaseEntity entity) {
		String providerName = "(已删除的提供商)";
		String model = "";
		try {
			ProviderEntity provider = providers.require(entity.getEmbeddingProviderId());
			providerName = provider.getName();
			model = provider.getDefaultModel() == null ? "" : provider.getDefaultModel();
		}
		catch (RuntimeException ex) {
			providerName = "(已删除的提供商)";
		}
		return new KnowledgeDtos.View(
				entity.getId(),
				entity.getName(),
				entity.getDescription() == null ? "" : entity.getDescription(),
				entity.getEmbeddingProviderId(),
				providerName,
				model,
				entity.getEmbeddingDim(),
				entity.getTopK(),
				entity.getChunkSize(),
				entity.getChunkOverlap(),
				(int) documents.countByKnowledgeBaseId(entity.getId()));
	}

	private KnowledgeDtos.DocumentView toDocument(KnowledgeDocumentEntity entity) {
		return new KnowledgeDtos.DocumentView(
				entity.getId(),
				entity.getKnowledgeBaseId(),
				entity.getName(),
				entity.getStatus(),
				entity.getErrorMessage() == null ? "" : entity.getErrorMessage(),
				entity.getCharCount(),
				(int) chunks.countByDocumentId(entity.getId()));
	}

	private static int clamp(Integer value, int min, int max, int fallback) {
		if (value == null) {
			return fallback;
		}
		return Math.min(max, Math.max(min, value));
	}

	private static String clipName(String name) {
		String value = name == null ? "" : name.trim();
		if (value.isEmpty()) {
			return "未命名文档";
		}
		if (value.length() <= 120) {
			return value;
		}
		return value.substring(0, 120);
	}

	private static String reason(RuntimeException ex) {
		if (ex instanceof ResponseStatusException status && status.getReason() != null && !status.getReason().isBlank()) {
			return status.getReason();
		}
		return ex.getMessage() == null ? "向量化失败" : ex.getMessage();
	}

	private static String clip(String text) {
		String value = text == null ? "" : text.replaceAll("\\s+", " ").trim();
		if (value.length() <= 300) {
			return value;
		}
		return value.substring(0, 300);
	}

	private static String excerpt(String content) {
		String value = content == null ? "" : content.trim().replaceAll("[ \\t\\x0B\\f]+", " ");
		if (value.length() <= 180) {
			return value;
		}
		return value.substring(0, 180) + "…";
	}

	private record Snippet(double score, String baseName, String documentName, String content) {
	}

	public record Hit(String knowledgeBase, String document, String content) {
	}

	public record Retrieval(String prompt, List<Hit> hits, boolean searched) {

		public static Retrieval empty() {
			return new Retrieval("", List.of(), false);
		}

		public static Retrieval miss() {
			return new Retrieval("", List.of(), true);
		}

		public String prompt() {
			return prompt == null ? "" : prompt;
		}

		public List<Hit> hits() {
			return hits == null ? List.of() : hits;
		}

	}

}
