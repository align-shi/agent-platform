package com.agentplatform.hub.httptool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.crypto.SecretCipher;

@Service
public class HttpToolService {

	static final int MAX_TOOLS = 30;
	private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
	private static final Set<String> AUTHS = Set.of("NONE", "BEARER", "HEADER");
	private static final Set<String> INS = Set.of("query", "path", "header", "body");
	private static final Set<String> TYPES = Set.of("string", "number", "integer", "boolean");

	private final HttpConnectorRepository connectors;
	private final HttpToolRepository tools;
	private final AgentHttpToolRepository bindings;
	private final HttpToolInvoker invoker;
	private final SecretCipher cipher;

	public HttpToolService(
			HttpConnectorRepository connectors,
			HttpToolRepository tools,
			AgentHttpToolRepository bindings,
			HttpToolInvoker invoker,
			SecretCipher cipher) {
		this.connectors = connectors;
		this.tools = tools;
		this.bindings = bindings;
		this.invoker = invoker;
		this.cipher = cipher;
	}

	@Transactional(readOnly = true)
	public List<HttpToolDtos.View> list() {
		return connectors.findAll().stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public HttpConnectorEntity requireConnector(String id) {
		return connectors.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "连接器不存在"));
	}

	@Transactional(readOnly = true)
	public HttpToolEntity requireTool(String connectorId, String toolId) {
		HttpToolEntity tool = tools.findById(toolId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工具不存在"));
		if (tool.getConnectorId() == null || !tool.getConnectorId().equals(connectorId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工具不存在");
		}
		return tool;
	}

	@Transactional(readOnly = true)
	public List<String> idsForAgent(String agentId) {
		return bindings.findByAgentIdOrderByHttpToolIdAsc(agentId).stream()
				.map(AgentHttpToolEntity::getHttpToolId)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<HttpToolEntity> boundEnabled(String agentId) {
		List<HttpToolEntity> result = new ArrayList<>();
		for (String connectorId : idsForAgent(agentId)) {
			connectors.findById(connectorId).filter(HttpConnectorEntity::isEnabled).ifPresent((connector) -> {
				for (HttpToolEntity tool : tools.findByConnectorIdOrderBySortOrderAsc(connector.getId())) {
					if (tool.isEnabled()) {
						result.add(tool);
					}
				}
			});
		}
		return result;
	}

	@Transactional
	public HttpToolDtos.View create(HttpToolDtos.UpsertRequest request) {
		HttpConnectorEntity connector = new HttpConnectorEntity();
		connector.setId(UUID.randomUUID().toString());
		applyConnector(connector, request, true);
		connectors.save(connector);
		replaceTools(connector, request.tools());
		return toView(connector);
	}

	@Transactional
	public HttpToolDtos.View update(String id, HttpToolDtos.UpsertRequest request) {
		HttpConnectorEntity connector = requireConnector(id);
		applyConnector(connector, request, false);
		connectors.save(connector);
		replaceTools(connector, request.tools());
		return toView(connector);
	}

	@Transactional
	public void delete(String id) {
		if (!connectors.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "连接器不存在");
		}
		bindings.deleteByHttpToolId(id);
		tools.deleteByConnectorId(id);
		connectors.deleteById(id);
	}

	@Transactional
	public void replaceBindings(String agentId, List<String> connectorIds) {
		bindings.deleteByAgentId(agentId);
		if (connectorIds == null) {
			return;
		}
		List<AgentHttpToolEntity> rows = new ArrayList<>();
		for (String connectorId : connectorIds) {
			if (connectorId == null || connectorId.isBlank()) {
				continue;
			}
			String normalized = connectorId.trim();
			if (rows.stream().anyMatch((row) -> row.getHttpToolId().equals(normalized))) {
				continue;
			}
			requireConnector(normalized);
			AgentHttpToolEntity row = new AgentHttpToolEntity();
			row.setId(UUID.randomUUID().toString());
			row.setAgentId(agentId);
			row.setHttpToolId(normalized);
			rows.add(row);
		}
		bindings.saveAll(rows);
	}

	@Transactional
	public void deleteBindingsForAgent(String agentId) {
		bindings.deleteByAgentId(agentId);
	}

	public List<Map<String, Object>> definitions(List<HttpToolEntity> boundTools) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (HttpToolEntity tool : boundTools) {
			list.add(definition(tool));
		}
		return list;
	}

	public String invoke(HttpToolEntity tool, String argumentsJson) {
		return invoke(tool, argumentsJson, null);
	}

	public String invoke(HttpToolEntity tool, String argumentsJson, String secretOverride) {
		HttpConnectorEntity connector = requireConnector(tool.getConnectorId());
		String secret = secretOverride == null ? "" : secretOverride.trim();
		if (secret.isBlank() && connector.getSecretCipher() != null && !connector.getSecretCipher().isBlank()) {
			secret = cipher.decrypt(connector.getSecretCipher());
		}
		try {
			return invoker.invoke(tool, argumentsJson, secret, connector);
		}
		catch (RuntimeException ex) {
			return "ERROR: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
		}
	}

	private void applyConnector(HttpConnectorEntity entity, HttpToolDtos.UpsertRequest request, boolean creating) {
		String authType = request.authType() == null || request.authType().isBlank()
				? "NONE"
				: request.authType().trim().toUpperCase(Locale.ROOT);
		if (!AUTHS.contains(authType)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的鉴权方式");
		}
		if (!"NONE".equals(authType) && creating && (request.secret() == null || request.secret().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该鉴权方式需要填写密钥");
		}
		entity.setName(request.name().trim());
		entity.setDescription(request.description() == null ? "" : request.description().trim());
		entity.setProtocol("DECLARATIVE");
		entity.setAuthType(authType);
		entity.setAuthHeader(request.authHeader() == null || request.authHeader().isBlank() ? "Authorization" : request.authHeader().trim());
		entity.setTimeoutMs(request.timeoutMs() == null ? 15000 : Math.min(60000, Math.max(1000, request.timeoutMs())));
		entity.setEnabled(request.enabled() == null || request.enabled());
		entity.setForwardCredentials(Boolean.TRUE.equals(request.forwardCredentials()));
		if (request.secret() != null && !request.secret().isBlank()) {
			String secret = request.secret().trim();
			entity.setSecretCipher(cipher.encrypt(secret));
			entity.setKeyLast4(secret.length() <= 4 ? secret : secret.substring(secret.length() - 4));
		}
		else if ("NONE".equals(authType)) {
			entity.setSecretCipher(null);
			entity.setKeyLast4(null);
		}
	}

	private void replaceTools(HttpConnectorEntity connector, List<HttpToolDtos.ToolUpsert> incoming) {
		List<HttpToolDtos.ToolUpsert> items = incoming == null ? List.of() : incoming;
		if (items.size() > MAX_TOOLS) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "每个连接器最多 " + MAX_TOOLS + " 个工具");
		}
		Set<String> names = new LinkedHashSet<>();
		List<HttpToolEntity> existing = tools.findByConnectorIdOrderBySortOrderAsc(connector.getId());
		Set<String> keep = new LinkedHashSet<>();
		for (HttpToolDtos.ToolUpsert item : items) {
			if (item.id() != null && !item.id().isBlank() && !item.id().startsWith("local-")) {
				keep.add(item.id());
			}
		}
		for (HttpToolEntity leftover : existing) {
			if (!keep.contains(leftover.getId())) {
				tools.delete(leftover);
			}
		}
		tools.flush();
		int order = 0;
		for (HttpToolDtos.ToolUpsert item : items) {
			HttpToolEntity entity = resolveTool(connector, existing, item);
			applyTool(entity, item);
			if (!names.add(entity.getToolName())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同一连接器内工具名不能重复: " + entity.getToolName());
			}
			if (entity.getId() == null) {
				entity.setId(UUID.randomUUID().toString());
			}
			entity.setConnectorId(connector.getId());
			entity.setSortOrder(order++);
			tools.save(entity);
		}
	}

	private HttpToolEntity resolveTool(
			HttpConnectorEntity connector,
			List<HttpToolEntity> existing,
			HttpToolDtos.ToolUpsert item) {
		if (item.id() == null || item.id().isBlank() || item.id().startsWith("local-")) {
			HttpToolEntity created = new HttpToolEntity();
			created.setConnectorId(connector.getId());
			return created;
		}
		for (HttpToolEntity current : existing) {
			if (current.getId().equals(item.id())) {
				return current;
			}
		}
		HttpToolEntity created = new HttpToolEntity();
		created.setConnectorId(connector.getId());
		return created;
	}

	private void applyTool(HttpToolEntity entity, HttpToolDtos.ToolUpsert request) {
		String toolName = HttpToolRequest.normalizeToolName(request.toolName());
		try {
			HttpToolRequest.validateToolName(toolName);
			HttpToolRequest.validateUrl(request.url());
		}
		catch (IllegalArgumentException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
		if (entity.getId() == null) {
			if (tools.existsByToolNameIgnoreCase(toolName)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工具名已存在: " + toolName);
			}
		}
		else if (tools.existsByToolNameIgnoreCaseAndIdNot(toolName, entity.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工具名已存在: " + toolName);
		}
		String method = request.method().trim().toUpperCase(Locale.ROOT);
		if (!METHODS.contains(method)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的 HTTP 方法");
		}
		entity.setName(request.name().trim());
		entity.setToolName(toolName);
		entity.setDescription(request.description() == null ? "" : request.description().trim());
		entity.setMethod(method);
		entity.setUrl(request.url().trim());
		entity.setAuthType("NONE");
		entity.setEnabled(request.enabled() == null || request.enabled());
		entity.setParametersJson(JsonLite.paramsToJson(normalizeParams(request.parameters())));
	}

	private List<HttpToolParam> normalizeParams(List<HttpToolParam> raw) {
		List<HttpToolParam> params = new ArrayList<>();
		if (raw == null) {
			return params;
		}
		for (HttpToolParam item : raw) {
			if (item == null || item.name() == null || item.name().isBlank()) {
				continue;
			}
			String name = item.name().trim();
			if (!name.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数名不合法: " + name);
			}
			if (params.stream().anyMatch((existing) -> existing.name().equals(name))) {
				continue;
			}
			String in = item.in() == null ? "query" : item.in().trim().toLowerCase(Locale.ROOT);
			String type = item.type() == null ? "string" : item.type().trim().toLowerCase(Locale.ROOT);
			if (!INS.contains(in) || !TYPES.contains(type)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "参数位置或类型不合法: " + name);
			}
			params.add(new HttpToolParam(name, in, type, item.required(), item.description() == null ? "" : item.description().trim()));
		}
		return params;
	}

	private Map<String, Object> definition(HttpToolEntity tool) {
		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();
		for (HttpToolParam param : tool.parameters()) {
			Map<String, Object> spec = new LinkedHashMap<>();
			spec.put("type", jsonSchemaType(param.type()));
			spec.put("description", param.description() == null || param.description().isBlank()
					? param.in() + " 参数"
					: param.description());
			properties.put(param.name(), spec);
			if (param.required()) {
				required.add(param.name());
			}
		}
		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("type", "object");
		parameters.put("properties", properties);
		parameters.put("required", required);
		Map<String, Object> function = new LinkedHashMap<>();
		function.put("name", tool.getToolName());
		function.put("description", toolDescription(tool));
		function.put("parameters", parameters);
		Map<String, Object> def = new LinkedHashMap<>();
		def.put("type", "function");
		def.put("function", function);
		return def;
	}

	private static String toolDescription(HttpToolEntity tool) {
		StringBuilder text = new StringBuilder();
		if (tool.getDescription() != null && !tool.getDescription().isBlank()) {
			text.append(tool.getDescription().trim());
		}
		else {
			text.append(tool.getName());
		}
		text.append("。调用 ").append(tool.getMethod()).append(' ').append(tool.getUrl());
		return text.toString();
	}

	private static String jsonSchemaType(String type) {
		if ("integer".equals(type)) {
			return "integer";
		}
		if ("number".equals(type)) {
			return "number";
		}
		if ("boolean".equals(type)) {
			return "boolean";
		}
		return "string";
	}

	private HttpToolDtos.View toView(HttpConnectorEntity entity) {
		List<HttpToolDtos.ToolView> nested = tools.findByConnectorIdOrderBySortOrderAsc(entity.getId()).stream()
				.map((tool) -> new HttpToolDtos.ToolView(
						tool.getId(),
						tool.getName(),
						tool.getToolName(),
						tool.getDescription(),
						tool.getMethod(),
						tool.getUrl(),
						tool.isEnabled(),
						tool.parameters()))
				.toList();
		return new HttpToolDtos.View(
				entity.getId(),
				entity.getName(),
				entity.getDescription(),
				entity.getProtocol() == null ? "DECLARATIVE" : entity.getProtocol(),
				entity.getAuthType(),
				entity.getAuthHeader(),
				entity.getSecretCipher() != null && !entity.getSecretCipher().isBlank(),
				entity.getKeyLast4() == null ? "" : entity.getKeyLast4(),
				entity.getTimeoutMs() == null ? 15000 : entity.getTimeoutMs(),
				entity.isEnabled(),
				entity.isForwardCredentials(),
				nested);
	}

}
