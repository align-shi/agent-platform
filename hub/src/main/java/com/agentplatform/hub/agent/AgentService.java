package com.agentplatform.hub.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.conversation.ConversationService;
import com.agentplatform.hub.knowledge.KnowledgeService;
import com.agentplatform.hub.httptool.HttpToolService;
import com.agentplatform.hub.mcp.RemoteMcpService;
import com.agentplatform.hub.provider.ProviderEntity;
import com.agentplatform.hub.provider.ProviderService;
import com.agentplatform.hub.provider.ProviderType;
import com.agentplatform.hub.skill.AgentSkillEntity;
import com.agentplatform.hub.skill.AgentSkillRepository;
import com.agentplatform.hub.skill.SkillService;
import com.agentplatform.hub.workflow.WorkflowService;

@Service
public class AgentService {

	private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,63}");

	private final AgentRepository repository;
	private final ProviderService providers;
	private final ConversationService conversations;
	private final AgentSkillRepository agentSkills;
	private final SkillService skills;
	private final HttpToolService httpTools;
	private final RemoteMcpService remoteMcps;
	private final KnowledgeService knowledge;
	private final WorkflowService workflows;

	public AgentService(
			AgentRepository repository,
			ProviderService providers,
			ConversationService conversations,
			AgentSkillRepository agentSkills,
			SkillService skills,
			HttpToolService httpTools,
			RemoteMcpService remoteMcps,
			KnowledgeService knowledge,
			@Lazy WorkflowService workflows) {
		this.repository = repository;
		this.providers = providers;
		this.conversations = conversations;
		this.agentSkills = agentSkills;
		this.skills = skills;
		this.httpTools = httpTools;
		this.remoteMcps = remoteMcps;
		this.knowledge = knowledge;
		this.workflows = workflows;
	}

	@Transactional(readOnly = true)
	public List<AgentDtos.View> list() {
		return repository.findAll().stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public AgentEntity require(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
	}

	@Transactional(readOnly = true)
	public AgentEntity requireByCode(String code) {
		String normalized = code == null ? "" : code.trim();
		if (!CODE.matcher(normalized).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "唯一编码须以字母开头，只能包含字母、数字、下划线和中划线");
		}
		return repository.findByCode(normalized)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found"));
	}

	@Transactional(readOnly = true)
	public List<String> skillIds(String agentId) {
		return agentSkills.findByAgentIdOrderBySkillIdAsc(agentId).stream()
				.map(AgentSkillEntity::getSkillId)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<String> httpToolIds(String agentId) {
		return httpTools.idsForAgent(agentId);
	}

	@Transactional(readOnly = true)
	public List<String> mcpServerIds(String agentId) {
		return remoteMcps.idsForAgent(agentId);
	}

	@Transactional(readOnly = true)
	public List<String> knowledgeBaseIds(String agentId) {
		return knowledge.idsForAgent(agentId);
	}

	@Transactional(readOnly = true)
	public List<String> workflowIds(String agentId) {
		return workflows.idsForAgent(agentId);
	}

	@Transactional
	public AgentDtos.View create(AgentDtos.UpsertRequest request) {
		AgentEntity entity = new AgentEntity();
		entity.setId(UUID.randomUUID().toString());
		apply(entity, request);
		AgentEntity saved = repository.save(entity);
		replaceSkills(saved.getId(), request.skillIds());
		httpTools.replaceBindings(saved.getId(), request.httpToolIds());
		remoteMcps.replaceBindings(saved.getId(), request.mcpServerIds());
		knowledge.replaceBindings(saved.getId(), request.knowledgeBaseIds());
		workflows.replaceBindings(saved.getId(), request.workflowIds());
		return toView(saved);
	}

	@Transactional
	public AgentDtos.View update(String id, AgentDtos.UpsertRequest request) {
		AgentEntity entity = require(id);
		apply(entity, request);
		AgentEntity saved = repository.save(entity);
		replaceSkills(saved.getId(), request.skillIds());
		httpTools.replaceBindings(saved.getId(), request.httpToolIds());
		remoteMcps.replaceBindings(saved.getId(), request.mcpServerIds());
		knowledge.replaceBindings(saved.getId(), request.knowledgeBaseIds());
		workflows.replaceBindings(saved.getId(), request.workflowIds());
		return toView(saved);
	}

	@Transactional
	public void delete(String id) {
		if (!repository.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent not found");
		}
		agentSkills.deleteByAgentId(id);
		httpTools.deleteBindingsForAgent(id);
		remoteMcps.deleteBindingsForAgent(id);
		knowledge.deleteBindingsForAgent(id);
		workflows.deleteBindingsForAgent(id);
		conversations.deleteByAgentId(id);
		repository.deleteById(id);
	}

	private void apply(AgentEntity entity, AgentDtos.UpsertRequest request) {
		ProviderEntity provider = providers.require(request.providerId());
		if (provider.getType() != ProviderType.CHAT) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is not a chat model");
		}
		entity.setName(request.name().trim());
		entity.setCode(normalizeCode(entity.getId(), request.code()));
		entity.setSystemPrompt(blankToEmpty(request.systemPrompt()));
		entity.setProviderId(provider.getId());
		entity.setModel(request.model().trim());
		entity.setMemoryMode(MemoryMode.from(request.memoryMode()).name());
		entity.setSummarizeWhenTokens(clamp(request.summarizeWhenTokens(), 1000, 200000, 8000));
		entity.setKeepLastMessages(clamp(request.keepLastMessages(), 2, 100, 10));
	}

	@Transactional
	public void saveFacts(String id, String facts) {
		AgentEntity entity = require(id);
		entity.setMemoryFacts(facts == null ? "" : facts.trim());
		repository.save(entity);
	}

	@Transactional
	public AgentDtos.View clearFacts(String id) {
		saveFacts(id, "");
		return toView(require(id));
	}

	private void replaceSkills(String agentId, List<String> skillIds) {
		agentSkills.deleteByAgentId(agentId);
		if (skillIds == null) {
			return;
		}
		List<AgentSkillEntity> rows = new ArrayList<>();
		for (String skillId : skillIds) {
			if (skillId == null || skillId.isBlank()) {
				continue;
			}
			String normalized = skillId.trim();
			if (rows.stream().anyMatch((row) -> row.getSkillId().equals(normalized))) {
				continue;
			}
			skills.require(normalized);
			AgentSkillEntity row = new AgentSkillEntity();
			row.setId(UUID.randomUUID().toString());
			row.setAgentId(agentId);
			row.setSkillId(normalized);
			rows.add(row);
		}
		agentSkills.saveAll(rows);
	}

	private AgentDtos.View toView(AgentEntity entity) {
		String providerName;
		try {
			providerName = providers.require(entity.getProviderId()).getName();
		}
		catch (RuntimeException ex) {
			providerName = "(已删除的提供商)";
		}
		return new AgentDtos.View(
				entity.getId(),
				entity.getName(),
				entity.getCode() == null ? "" : entity.getCode(),
				entity.getSystemPrompt(),
				entity.getProviderId(),
				providerName,
				entity.getModel(),
				skillIds(entity.getId()),
				httpToolIds(entity.getId()),
				mcpServerIds(entity.getId()),
				knowledgeBaseIds(entity.getId()),
				workflowIds(entity.getId()),
				entity.resolvedMemoryMode().name(),
				entity.getSummarizeWhenTokens() == null ? 8000 : entity.getSummarizeWhenTokens(),
				entity.getKeepLastMessages() == null ? 10 : entity.getKeepLastMessages(),
				entity.getMemoryFacts() == null ? "" : entity.getMemoryFacts());
	}

	private String normalizeCode(String agentId, String raw) {
		String code = raw == null ? "" : raw.trim();
		if (!CODE.matcher(code).matches()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "唯一编码须以字母开头，只能包含字母、数字、下划线和中划线");
		}
		repository.findByCode(code).ifPresent((existing) -> {
			if (!existing.getId().equals(agentId)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "唯一编码已存在");
			}
		});
		return code;
	}

	private static String blankToEmpty(String value) {
		return value == null ? "" : value.trim();
	}

	private static int clamp(Integer value, int min, int max, int fallback) {
		if (value == null) {
			return fallback;
		}
		return Math.min(max, Math.max(min, value));
	}

}
