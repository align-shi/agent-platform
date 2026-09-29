package com.agentplatform.hub.mcp;

import java.time.Instant;
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
public class RemoteMcpService {

	private static final Set<String> AUTHS = Set.of("NONE", "BEARER", "HEADER");
	private static final Set<String> TRANSPORTS = Set.of("STREAMABLE", "SSE");
	private static final Set<String> RESERVED = Set.of("load_skill", "read_skill_resource", "run_skill_script");

	private final RemoteMcpRepository servers;
	private final AgentMcpRepository bindings;
	private final McpClient client;
	private final SecretCipher cipher;

	public RemoteMcpService(
			RemoteMcpRepository servers,
			AgentMcpRepository bindings,
			McpClient client,
			SecretCipher cipher) {
		this.servers = servers;
		this.bindings = bindings;
		this.client = client;
		this.cipher = cipher;
	}

	@Transactional(readOnly = true)
	public List<RemoteMcpDtos.View> list() {
		return servers.findAllByOrderByNameAsc().stream().map(this::toView).toList();
	}

	@Transactional(readOnly = true)
	public RemoteMcpEntity require(String id) {
		return servers.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "远程连接器不存在"));
	}

	@Transactional(readOnly = true)
	public List<String> idsForAgent(String agentId) {
		return bindings.findByAgentIdOrderByMcpServerIdAsc(agentId).stream()
				.map(AgentMcpEntity::getMcpServerId)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<BoundMcpTool> boundEnabled(String agentId, Set<String> reservedNames) {
		Set<String> taken = new LinkedHashSet<>();
		for (String name : RESERVED) {
			taken.add(name.toLowerCase(Locale.ROOT));
		}
		if (reservedNames != null) {
			for (String name : reservedNames) {
				if (name != null && !name.isBlank()) {
					taken.add(name.toLowerCase(Locale.ROOT));
				}
			}
		}
		List<BoundMcpTool> result = new ArrayList<>();
		for (String serverId : idsForAgent(agentId)) {
			servers.findById(serverId).filter(RemoteMcpEntity::isEnabled).ifPresent((server) -> {
				for (StoredTool tool : storedTools(server)) {
					String exposed = RemoteMcpNames.unique(tool.name(), taken, server.getId());
					taken.add(exposed.toLowerCase(Locale.ROOT));
					result.add(new BoundMcpTool(server, tool.name(), exposed, tool.description(), tool.inputSchema()));
				}
			});
		}
		return result;
	}

	@Transactional
	public RemoteMcpDtos.View create(RemoteMcpDtos.UpsertRequest request) {
		RemoteMcpEntity entity = new RemoteMcpEntity();
		entity.setId(UUID.randomUUID().toString());
		apply(entity, request, true);
		entity.prepareInsert();
		entity = servers.save(entity);
		discoverQuietly(entity, null);
		return toView(entity);
	}

	@Transactional
	public RemoteMcpDtos.View update(String id, RemoteMcpDtos.UpsertRequest request) {
		RemoteMcpEntity entity = require(id);
		apply(entity, request, false);
		entity = servers.save(entity);
		discoverQuietly(entity, null);
		return toView(entity);
	}

	@Transactional
	public void delete(String id) {
		if (!servers.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "远程连接器不存在");
		}
		bindings.deleteByMcpServerId(id);
		servers.deleteById(id);
	}

	@Transactional
	public void replaceBindings(String agentId, List<String> serverIds) {
		bindings.deleteByAgentId(agentId);
		if (serverIds == null) {
			return;
		}
		List<AgentMcpEntity> rows = new ArrayList<>();
		for (String serverId : serverIds) {
			if (serverId == null || serverId.isBlank()) {
				continue;
			}
			String normalized = serverId.trim();
			if (rows.stream().anyMatch((row) -> row.getMcpServerId().equals(normalized))) {
				continue;
			}
			require(normalized);
			AgentMcpEntity row = new AgentMcpEntity();
			row.setId(UUID.randomUUID().toString());
			row.setAgentId(agentId);
			row.setMcpServerId(normalized);
			rows.add(row);
		}
		bindings.saveAll(rows);
	}

	@Transactional
	public void deleteBindingsForAgent(String agentId) {
		bindings.deleteByAgentId(agentId);
	}

	@Transactional
	public RemoteMcpDtos.View refresh(String id, String secretOverride) {
		RemoteMcpEntity entity = require(id);
		try {
			discoverOrThrow(entity, secretOverride);
		}
		catch (RuntimeException ex) {
			entity.setLastError(ex.getMessage());
			servers.save(entity);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
		return toView(servers.save(entity));
	}

	@Transactional
	public String tryConnect(String id, String secretOverride) {
		RemoteMcpEntity entity = require(id);
		McpClient.DiscoverResult result;
		try {
			result = client.discover(target(entity, secretOverride));
		}
		catch (RuntimeException ex) {
			entity.setLastError(ex.getMessage());
			servers.save(entity);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
		}
		applyDiscover(entity, result);
		servers.save(entity);
		return formatTry(entity, result);
	}

	public List<Map<String, Object>> definitions(List<BoundMcpTool> tools) {
		List<Map<String, Object>> list = new ArrayList<>();
		for (BoundMcpTool tool : tools) {
			list.add(definition(tool));
		}
		return list;
	}

	public String invoke(BoundMcpTool tool, String argumentsJson) {
		Map<String, Object> arguments;
		try {
			arguments = argumentsJson == null || argumentsJson.isBlank() ? Map.of() : McpJson.object(argumentsJson);
		}
		catch (RuntimeException ex) {
			return "ERROR: 工具参数不是 JSON 对象";
		}
		return client.callTool(target(tool.server(), null), tool.originalName(), arguments);
	}

	private void discoverQuietly(RemoteMcpEntity entity, String secretOverride) {
		try {
			discoverOrThrow(entity, secretOverride);
		}
		catch (RuntimeException ex) {
			entity.setLastError(ex.getMessage());
		}
	}

	private void discoverOrThrow(RemoteMcpEntity entity, String secretOverride) {
		McpClient.DiscoverResult result = client.discover(target(entity, secretOverride));
		applyDiscover(entity, result);
	}

	private void applyDiscover(RemoteMcpEntity entity, McpClient.DiscoverResult result) {
		McpClient.Handshake handshake = result.handshake();
		entity.setProtocolVersion(handshake.protocolVersion());
		entity.setServerName(handshake.serverName());
		entity.setServerVersion(handshake.serverVersion());
		entity.setLastError(null);
		entity.setLastSyncedAt(Instant.now());
		List<Map<String, Object>> tools = new ArrayList<>();
		for (McpClient.McpTool tool : result.tools()) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("name", tool.name());
			item.put("description", tool.description() == null ? "" : tool.description());
			item.put("inputSchema", tool.inputSchema());
			tools.add(item);
		}
		entity.setToolsJson(McpJson.write(tools));
	}

	private McpClient.McpTarget target(RemoteMcpEntity entity, String secretOverride) {
		String secret = secretOverride == null ? "" : secretOverride.trim();
		if (secret.isBlank() && entity.getSecretCipher() != null && !entity.getSecretCipher().isBlank()) {
			secret = cipher.decrypt(entity.getSecretCipher());
		}
		int timeout = entity.getTimeoutMs() == null ? 15000 : Math.min(60000, Math.max(1000, entity.getTimeoutMs()));
		return new McpClient.McpTarget(
				entity.getUrl(),
				entity.getTransport() == null ? "STREAMABLE" : entity.getTransport(),
				entity.getAuthType(),
				entity.getAuthHeader(),
				secret,
				timeout);
	}

	private void apply(RemoteMcpEntity entity, RemoteMcpDtos.UpsertRequest request, boolean creating) {
		String authType = request.authType() == null || request.authType().isBlank()
				? "NONE"
				: request.authType().trim().toUpperCase(Locale.ROOT);
		if (!AUTHS.contains(authType)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的鉴权方式");
		}
		String transport = request.transport() == null || request.transport().isBlank()
				? "STREAMABLE"
				: request.transport().trim().toUpperCase(Locale.ROOT);
		if ("STREAMABLE_HTTP".equals(transport) || "HTTP".equals(transport)) {
			transport = "STREAMABLE";
		}
		if (!TRANSPORTS.contains(transport)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "传输只支持 Streamable HTTP 或 SSE");
		}
		String url = request.url().trim();
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 http/https 的 MCP 地址");
		}
		if (!"NONE".equals(authType) && creating && (request.secret() == null || request.secret().isBlank())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该鉴权方式需要填写密钥");
		}
		entity.setName(request.name().trim());
		entity.setDescription(request.description() == null ? "" : request.description().trim());
		entity.setUrl(url);
		entity.setTransport(transport);
		entity.setAuthType(authType);
		entity.setAuthHeader(request.authHeader() == null || request.authHeader().isBlank() ? "Authorization" : request.authHeader().trim());
		entity.setTimeoutMs(request.timeoutMs() == null ? 15000 : Math.min(60000, Math.max(1000, request.timeoutMs())));
		entity.setEnabled(request.enabled() == null || request.enabled());
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

	private List<StoredTool> storedTools(RemoteMcpEntity entity) {
		List<StoredTool> tools = new ArrayList<>();
		if (entity.getToolsJson() == null || entity.getToolsJson().isBlank()) {
			return tools;
		}
		Object raw;
		try {
			raw = McpJson.read(entity.getToolsJson());
		}
		catch (RuntimeException ex) {
			return tools;
		}
		if (!(raw instanceof List<?> items)) {
			return tools;
		}
		for (Object item : items) {
			if (!(item instanceof Map<?, ?> map)) {
				continue;
			}
			String name = map.get("name") == null ? "" : String.valueOf(map.get("name"));
			if (name.isBlank()) {
				continue;
			}
			String description = map.get("description") == null ? "" : String.valueOf(map.get("description"));
			Map<String, Object> schema = schema(map.get("inputSchema"));
			tools.add(new StoredTool(name, description, schema));
		}
		return tools;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> schema(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) map);
			if (!copy.containsKey("type")) {
				copy.put("type", "object");
			}
			if (!copy.containsKey("properties")) {
				copy.put("properties", new LinkedHashMap<>());
			}
			return copy;
		}
		Map<String, Object> empty = new LinkedHashMap<>();
		empty.put("type", "object");
		empty.put("properties", new LinkedHashMap<>());
		return empty;
	}

	private Map<String, Object> definition(BoundMcpTool tool) {
		Map<String, Object> function = new LinkedHashMap<>();
		function.put("name", tool.exposedName());
		function.put("description", toolDescription(tool));
		function.put("parameters", tool.inputSchema());
		Map<String, Object> def = new LinkedHashMap<>();
		def.put("type", "function");
		def.put("function", function);
		return def;
	}

	private static String toolDescription(BoundMcpTool tool) {
		StringBuilder text = new StringBuilder();
		if (tool.description() != null && !tool.description().isBlank()) {
			text.append(tool.description().trim());
		}
		else {
			text.append(tool.originalName());
		}
		text.append("。来自远程 MCP「").append(tool.server().getName()).append("」");
		if (!tool.exposedName().equals(tool.originalName())) {
			text.append("，原名 ").append(tool.originalName());
		}
		return text.toString();
	}

	private String formatTry(RemoteMcpEntity entity, McpClient.DiscoverResult result) {
		StringBuilder text = new StringBuilder();
		text.append("传输: ").append(entity.getTransport()).append('\n');
		text.append("地址: ").append(entity.getUrl()).append('\n');
		text.append(authSummary(entity)).append('\n');
		text.append("协议: ").append(nullToDash(result.handshake().protocolVersion())).append('\n');
		text.append("服务: ").append(nullToDash(result.handshake().serverName()));
		if (result.handshake().serverVersion() != null && !result.handshake().serverVersion().isBlank()) {
			text.append(' ').append(result.handshake().serverVersion());
		}
		text.append('\n');
		text.append("工具: ").append(result.tools().size()).append(" 个\n");
		for (McpClient.McpTool tool : result.tools()) {
			text.append("- ").append(tool.name());
			if (tool.description() != null && !tool.description().isBlank()) {
				text.append("：").append(tool.description());
			}
			text.append('\n');
		}
		return text.toString();
	}

	private static String authSummary(RemoteMcpEntity entity) {
		String type = entity.getAuthType() == null ? "NONE" : entity.getAuthType();
		if ("NONE".equals(type)) {
			return "鉴权: 无";
		}
		if (entity.getSecretCipher() == null || entity.getSecretCipher().isBlank()) {
			return "鉴权: 未携带密钥";
		}
		if ("BEARER".equals(type)) {
			return "鉴权: Bearer";
		}
		String name = entity.getAuthHeader() == null || entity.getAuthHeader().isBlank() ? "Authorization" : entity.getAuthHeader();
		return "鉴权: Header " + name;
	}

	private RemoteMcpDtos.View toView(RemoteMcpEntity entity) {
		List<RemoteMcpDtos.ToolView> tools = storedTools(entity).stream()
				.map((tool) -> new RemoteMcpDtos.ToolView(tool.name(), tool.description()))
				.toList();
		return new RemoteMcpDtos.View(
				entity.getId(),
				entity.getName(),
				entity.getDescription() == null ? "" : entity.getDescription(),
				entity.getUrl(),
				entity.getTransport() == null ? "STREAMABLE" : entity.getTransport(),
				entity.getAuthType(),
				entity.getAuthHeader(),
				entity.getSecretCipher() != null && !entity.getSecretCipher().isBlank(),
				entity.getKeyLast4() == null ? "" : entity.getKeyLast4(),
				entity.getTimeoutMs() == null ? 15000 : entity.getTimeoutMs(),
				entity.isEnabled(),
				entity.getServerName() == null ? "" : entity.getServerName(),
				entity.getServerVersion() == null ? "" : entity.getServerVersion(),
				entity.getProtocolVersion() == null ? "" : entity.getProtocolVersion(),
				entity.getLastError() == null ? "" : entity.getLastError(),
				entity.getLastSyncedAt() == null ? "" : entity.getLastSyncedAt().toString(),
				tools);
	}

	private static String nullToDash(String value) {
		return value == null || value.isBlank() ? "-" : value;
	}

	public record BoundMcpTool(
			RemoteMcpEntity server,
			String originalName,
			String exposedName,
			String description,
			Map<String, Object> inputSchema) {
	}

	private record StoredTool(String name, String description, Map<String, Object> inputSchema) {
	}

}
