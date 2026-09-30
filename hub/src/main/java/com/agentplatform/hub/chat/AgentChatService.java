package com.agentplatform.hub.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.agentplatform.hub.agent.AgentEntity;
import com.agentplatform.hub.agent.AgentService;
import com.agentplatform.hub.conversation.CitationJson;
import com.agentplatform.hub.conversation.ConversationDtos;
import com.agentplatform.hub.conversation.ConversationEntity;
import com.agentplatform.hub.conversation.ConversationService;
import com.agentplatform.hub.httptool.HttpToolEntity;
import com.agentplatform.hub.httptool.HttpToolService;
import com.agentplatform.hub.knowledge.KnowledgeService;
import com.agentplatform.hub.knowledge.KnowledgeService.Retrieval;
import com.agentplatform.hub.mcp.RemoteMcpService;
import com.agentplatform.hub.mcp.RemoteMcpService.BoundMcpTool;
import com.agentplatform.hub.memory.MemoryService;
import com.agentplatform.hub.memory.PreparedMemory;
import com.agentplatform.hub.provider.ProviderEntity;
import com.agentplatform.hub.provider.ProviderService;
import com.agentplatform.hub.provider.ProviderType;
import com.agentplatform.hub.skill.SkillService;
import com.agentplatform.hub.trace.CallTraceRecorder;
import com.agentplatform.hub.trace.CallTraceService;
import com.agentplatform.hub.trace.TraceTexts;
import com.agentplatform.hub.workflow.WorkflowEntity;
import com.agentplatform.hub.workflow.WorkflowService;

@Service
public class AgentChatService {

	private static final int MAX_TOOL_ROUNDS = 8;

	private final AgentService agents;
	private final ProviderService providers;
	private final ConversationService conversations;
	private final ChatProxyService chatProxy;
	private final SkillService skills;
	private final MemoryService memory;
	private final HttpToolService httpTools;
	private final RemoteMcpService remoteMcps;
	private final KnowledgeService knowledge;
	private final CallTraceService callTraces;
	private final WorkflowService workflows;

	public AgentChatService(
			AgentService agents,
			ProviderService providers,
			ConversationService conversations,
			ChatProxyService chatProxy,
			SkillService skills,
			MemoryService memory,
			HttpToolService httpTools,
			RemoteMcpService remoteMcps,
			KnowledgeService knowledge,
			CallTraceService callTraces,
			@Lazy WorkflowService workflows) {
		this.agents = agents;
		this.providers = providers;
		this.conversations = conversations;
		this.chatProxy = chatProxy;
		this.skills = skills;
		this.memory = memory;
		this.httpTools = httpTools;
		this.remoteMcps = remoteMcps;
		this.knowledge = knowledge;
		this.callTraces = callTraces;
		this.workflows = workflows;
	}

	public ChatDtos.Reply completeByCode(String code, String conversationId, String content) throws Exception {
		AgentEntity agent = agents.requireByCode(code);
		return stream(new ChatDtos.Request(agent.getId(), conversationId, content), ChatSink.silent());
	}

	public ChatDtos.Reply stream(ChatDtos.Request request, ChatSink sink) throws Exception {
		AgentEntity agent = agents.require(request.agentId());
		ProviderEntity provider = providers.require(agent.getProviderId());
		if (provider.getType() != ProviderType.CHAT) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider is not a chat model");
		}
		String userText = request.content().trim();
		ConversationEntity conversation = conversations.getOrCreate(agent.getId(), request.conversationId());
		conversations.append(conversation.getId(), "user", userText);
		sink.meta(conversation.getId());

		CallTraceRecorder trace = CallTraceRecorder.start(
				agent.getId(),
				agent.getName(),
				provider.getId(),
				agent.getModel(),
				conversation.getId(),
				userText);
		trace.systemPrompt(agent.getSystemPrompt());

		List<String> skillIds = agents.skillIds(agent.getId());
		List<HttpToolEntity> boundHttpTools = httpTools.boundEnabled(agent.getId());
		List<BoundMcpTool> boundMcpTools = remoteMcps.boundEnabled(agent.getId(), httpToolNames(boundHttpTools));
		List<WorkflowEntity> boundWorkflows = workflows.boundEnabled(agent.getId());
		String apiKey = providers.decryptApiKey(provider);
		String assistant = null;
		Exception failed = null;
		try {
			long retrievalMark = trace.mark();
			Retrieval retrieval = retrieveKnowledge(agent, userText, sink);
			trace.retrieval(TraceTexts.knowledge(retrieval), retrievalMark);
			if (skillIds.isEmpty() && boundHttpTools.isEmpty() && boundMcpTools.isEmpty() && boundWorkflows.isEmpty()) {
				assistant = runPlain(agent, provider, apiKey, conversation.getId(), retrieval, trace, sink);
			}
			else {
				assistant = runWithTools(
						agent,
						provider,
						apiKey,
						conversation.getId(),
						userText,
						skillIds,
						boundHttpTools,
						boundMcpTools,
						boundWorkflows,
						retrieval,
						trace,
						sink);
			}
			trace.assistant(assistant);
			if (assistant != null && !assistant.isBlank()) {
				conversations.append(conversation.getId(), "assistant", assistant, citationsJson(retrieval, assistant));
				memory.rememberTurn(agent, provider, apiKey, userText, assistant);
			}
		}
		catch (Exception ex) {
			failed = ex;
		}
		CallTraceRecorder.Draft draft = failed == null ? trace.success() : trace.failure(errorText(failed));
		callTraces.save(draft);
		if (failed != null) {
			throw failed;
		}
		return new ChatDtos.Reply(agent.getCode(), conversation.getId(), assistant == null ? "" : assistant, draft.id());
	}

	private String runPlain(
			AgentEntity agent,
			ProviderEntity provider,
			String apiKey,
			String conversationId,
			Retrieval retrieval,
			CallTraceRecorder trace,
			ChatSink sink) throws Exception {
		long prep = trace.mark();
		PreparedMemory prepared = memory.prepare(
				agent,
				provider,
				apiKey,
				conversationId,
				buildSystemPrompt(agent, List.of(), List.of(), List.of(), List.of(), retrieval));
		List<ChatDtos.Message> payload = new ArrayList<>();
		if (!prepared.system().isBlank()) {
			payload.add(new ChatDtos.Message("system", prepared.system()));
		}
		for (ConversationDtos.MessageView message : prepared.history()) {
			payload.add(new ChatDtos.Message(message.role(), message.content()));
		}
		trace.beginRound();
		trace.context(
				prepared.system(),
				"",
				"",
				TraceTexts.transcript(prepared.history()),
				TraceTexts.knowledge(retrieval),
				prep);
		long mark = trace.mark();
		ChatRound result;
		try {
			result = chatProxy.stream(provider, apiKey, agent.getModel(), payload, sink);
		}
		catch (RuntimeException ex) {
			trace.modelFailed(agent.getModel(), errorText(ex), 0, mark);
			throw ex;
		}
		trace.model(
				agent.getModel(),
				result.content(),
				result.usage().promptTokens(),
				result.usage().completionTokens(),
				result.usage().totalTokens(),
				0,
				mark);
		finish(sink, retrieval, result.content());
		return result.content();
	}

	private String runWithTools(
			AgentEntity agent,
			ProviderEntity provider,
			String apiKey,
			String conversationId,
			String userText,
			List<String> skillIds,
			List<HttpToolEntity> boundHttpTools,
			List<BoundMcpTool> boundMcpTools,
			List<WorkflowEntity> boundWorkflows,
			Retrieval retrieval,
			CallTraceRecorder trace,
			ChatSink sink) throws Exception {
		long prep = trace.mark();
		PreparedMemory prepared = memory.prepare(
				agent,
				provider,
				apiKey,
				conversationId,
				buildSystemPrompt(agent, skillIds, boundHttpTools, boundMcpTools, boundWorkflows, retrieval));
		List<Map<String, Object>> messages = new ArrayList<>();
		if (!prepared.system().isBlank()) {
			messages.add(Map.of("role", "system", "content", prepared.system()));
		}
		for (ConversationDtos.MessageView message : prepared.history()) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("role", message.role());
			item.put("content", message.content());
			messages.add(item);
		}
		List<Map<String, Object>> tools = new ArrayList<>();
		if (!skillIds.isEmpty()) {
			tools.addAll(SkillTools.definitions());
		}
		tools.addAll(httpTools.definitions(boundHttpTools));
		tools.addAll(remoteMcps.definitions(boundMcpTools));
		if (!boundWorkflows.isEmpty()) {
			tools.addAll(workflows.definitions());
		}
		String skillCatalog = skills.catalogPrompt(skillIds);
		String knowledge = TraceTexts.knowledge(retrieval);
		trace.beginRound();
		StringBuilder visible = new StringBuilder();
		for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
			boolean last = round == MAX_TOOL_ROUNDS - 1;
			List<Map<String, Object>> offered = last ? List.of() : tools;
			long contextMark = round == 0 ? prep : trace.mark();
			trace.context(
					TraceTexts.systemOf(messages),
					skillCatalog,
					TraceTexts.tools(offered),
					TraceTexts.messages(messages),
					knowledge,
					contextMark);
			long mark = trace.mark();
			ChatRound result;
			try {
				result = chatProxy.streamRound(provider, apiKey, agent.getModel(), messages, offered, sink);
				trace.model(
						agent.getModel(),
						result.content(),
						result.usage().promptTokens(),
						result.usage().completionTokens(),
						result.usage().totalTokens(),
						offered.size(),
						mark);
			}
			catch (RuntimeException ex) {
				trace.modelFailed(agent.getModel(), errorText(ex), offered.size(), mark);
				throw ex;
			}
			if (result.content() != null && !result.content().isEmpty()) {
				visible.append(result.content());
			}
			if (!result.hasToolCalls()) {
				break;
			}
			sink.status(statusText(result.toolCalls()));
			messages.add(assistantToolMessage(result));
			for (ChatRound.ToolCall call : result.toolCalls()) {
				long toolMark = trace.mark();
				String output = executeTool(
						agent.getId(),
						userText,
						skillIds,
						boundHttpTools,
						boundMcpTools,
						boundWorkflows,
						call.name(),
						call.arguments());
				trace.tool(
						call.name(),
						toolKind(call.name(), boundHttpTools, boundMcpTools),
						call.arguments(),
						output,
						toolMark);
				Map<String, Object> toolMessage = new LinkedHashMap<>();
				toolMessage.put("role", "tool");
				toolMessage.put("tool_call_id", call.id());
				toolMessage.put("name", call.name());
				toolMessage.put("content", output);
				messages.add(toolMessage);
			}
		}
		String assistant = visible.toString();
		if (assistant.isBlank()) {
			long mark = trace.mark();
			try {
				ChatRound fallback = chatProxy.complete(provider, apiKey, agent.getModel(), messages, List.of());
				trace.model(
						agent.getModel(),
						fallback.content(),
						fallback.usage().promptTokens(),
						fallback.usage().completionTokens(),
						fallback.usage().totalTokens(),
						0,
						mark);
				assistant = fallback.content() == null ? "" : fallback.content();
			}
			catch (RuntimeException ex) {
				trace.modelFailed(agent.getModel(), errorText(ex), 0, mark);
				throw ex;
			}
			if (!assistant.isBlank()) {
				sink.delta(ChatProxyService.deltaPayload(assistant));
			}
		}
		finish(sink, retrieval, assistant);
		return assistant;
	}

	private String executeTool(
			String agentId,
			String userText,
			List<String> allowedSkillIds,
			List<HttpToolEntity> boundHttpTools,
			List<BoundMcpTool> boundMcpTools,
			List<WorkflowEntity> boundWorkflows,
			String name,
			String arguments) {
		if ("run_workflow".equals(name)) {
			String workflowId = ToolArguments.string(arguments, "workflow_id");
			boolean allowed = boundWorkflows.stream().anyMatch((workflow) -> workflow.getId().equals(workflowId));
			if (!allowed) {
				return "ERROR: workflow is not bound to this agent";
			}
			String input = ToolArguments.string(arguments, "input");
			return workflows.invokeAsTool(agentId, workflowId, input.isBlank() ? userText : input);
		}
		if ("load_skill".equals(name) || "read_skill_resource".equals(name) || "run_skill_script".equals(name)) {
			String skillId = ToolArguments.string(arguments, "skill_id");
			if (skillId.isBlank() || !allowedSkillIds.contains(skillId)) {
				return "ERROR: skill is not bound to this agent";
			}
			try {
				return switch (name) {
					case "load_skill" -> skills.loadForTool(skillId);
					case "read_skill_resource" -> readResource(skillId, ToolArguments.string(arguments, "path"));
					case "run_skill_script" -> skills.runScript(
							skillId,
							ToolArguments.string(arguments, "script"),
							ToolArguments.strings(arguments, "args"));
					default -> "ERROR: unknown tool " + name;
				};
			}
			catch (ResponseStatusException ex) {
				return "ERROR: " + (ex.getReason() == null ? ex.getMessage() : ex.getReason());
			}
			catch (RuntimeException ex) {
				return "ERROR: " + ex.getMessage();
			}
		}
		for (HttpToolEntity tool : boundHttpTools) {
			if (tool.getToolName().equals(name)) {
				return httpTools.invoke(tool, arguments);
			}
		}
		for (BoundMcpTool tool : boundMcpTools) {
			if (tool.exposedName().equals(name)) {
				return remoteMcps.invoke(tool, arguments);
			}
		}
		return "ERROR: tool is not bound to this agent";
	}

	private String readResource(String skillId, String path) {
		if (path.replace('\\', '/').startsWith("scripts/")) {
			return "ERROR: use run_skill_script for files in scripts/";
		}
		return skills.readFile(skillId, path).content();
	}

	private static Map<String, Object> assistantToolMessage(ChatRound round) {
		Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "assistant");
		message.put("content", round.content() == null ? "" : round.content());
		List<Map<String, Object>> calls = new ArrayList<>();
		for (ChatRound.ToolCall call : round.toolCalls()) {
			Map<String, Object> function = new LinkedHashMap<>();
			function.put("name", call.name());
			function.put("arguments", call.arguments() == null ? "{}" : call.arguments());
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", call.id());
			item.put("type", "function");
			item.put("function", function);
			calls.add(item);
		}
		message.put("tool_calls", calls);
		return message;
	}

	private static String statusText(List<ChatRound.ToolCall> calls) {
		StringBuilder text = new StringBuilder();
		for (ChatRound.ToolCall call : calls) {
			if (text.length() > 0) {
				text.append("；");
			}
			text.append(switch (call.name()) {
				case "load_skill" -> "正在加载技能 " + ToolArguments.string(call.arguments(), "skill_id");
				case "read_skill_resource" -> "正在读取 " + ToolArguments.string(call.arguments(), "path");
				case "run_skill_script" -> "正在执行脚本 " + ToolArguments.string(call.arguments(), "script");
				case "run_workflow" -> "正在运行工作流";
				default -> "正在调用 " + call.name();
			});
		}
		return text.toString();
	}

	private Retrieval retrieveKnowledge(AgentEntity agent, String query, ChatSink sink) throws Exception {
		if (knowledge.idsForAgent(agent.getId()).isEmpty()) {
			return Retrieval.empty();
		}
		sink.status("正在检索知识库");
		try {
			return knowledge.retrieve(agent.getId(), query);
		}
		catch (RuntimeException ex) {
			String reason = ex instanceof ResponseStatusException status && status.getReason() != null
					? status.getReason()
					: ex.getMessage();
			String text = reason == null || reason.isBlank() ? "知识库检索失败" : "知识库检索失败：" + clip(reason);
			sink.status(text);
			return Retrieval.miss();
		}
	}

	private void finish(ChatSink sink, Retrieval retrieval, String assistant) throws Exception {
		String citations = citationsJson(retrieval, assistant);
		if (!citations.isBlank()) {
			sink.citations(citations);
		}
		sink.done();
	}

	private static String citationsJson(Retrieval retrieval, String assistant) {
		List<KnowledgeService.Hit> used = KnowledgePrompt.cited(retrieval, assistant);
		if (used.isEmpty()) {
			return "";
		}
		List<CitationJson.Item> items = new ArrayList<>();
		for (KnowledgeService.Hit hit : used) {
			items.add(new CitationJson.Item(hit.knowledgeBase(), hit.document(), hit.content()));
		}
		return CitationJson.encode(items);
	}

	private String buildSystemPrompt(
			AgentEntity agent,
			List<String> skillIds,
			List<HttpToolEntity> boundHttpTools,
			List<BoundMcpTool> boundMcpTools,
			List<WorkflowEntity> boundWorkflows,
			Retrieval retrieval) {
		StringBuilder system = new StringBuilder();
		if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
			system.append(agent.getSystemPrompt().trim());
		}
		String catalog = skills.catalogPrompt(skillIds);
		if (!catalog.isBlank()) {
			if (system.length() > 0) {
				system.append("\n\n");
			}
			system.append(catalog);
		}
		if (boundHttpTools != null && !boundHttpTools.isEmpty()) {
			if (system.length() > 0) {
				system.append("\n\n");
			}
			system.append("可用 HTTP 工具（按登记的接口调用，参数见工具定义）：");
			for (HttpToolEntity tool : boundHttpTools) {
				system.append("\n- ").append(tool.getToolName()).append("：")
						.append(tool.getName())
						.append("，").append(tool.getMethod()).append(' ').append(tool.getUrl());
			}
		}
		if (boundMcpTools != null && !boundMcpTools.isEmpty()) {
			if (system.length() > 0) {
				system.append("\n\n");
			}
			system.append("可用远程 MCP 工具（按对方服务发现的接口调用）：");
			for (BoundMcpTool tool : boundMcpTools) {
				system.append("\n- ").append(tool.exposedName()).append("：")
						.append(tool.server().getName());
				if (tool.description() != null && !tool.description().isBlank()) {
					system.append("，").append(tool.description().trim());
				}
			}
		}
		String workflowCatalog = workflows.catalogPrompt(boundWorkflows);
		if (!workflowCatalog.isBlank()) {
			if (system.length() > 0) {
				system.append("\n\n");
			}
			system.append(workflowCatalog);
		}
		KnowledgePrompt.append(system, retrieval);
		return system.toString();
	}

	private static String clip(String text) {
		String value = text.replaceAll("\\s+", " ").trim();
		if (value.length() <= 120) {
			return value;
		}
		return value.substring(0, 120);
	}

	private static String toolKind(
			String name,
			List<HttpToolEntity> boundHttpTools,
			List<BoundMcpTool> boundMcpTools) {
		if ("run_workflow".equals(name)) {
			return "workflow";
		}
		if ("load_skill".equals(name) || "read_skill_resource".equals(name) || "run_skill_script".equals(name)) {
			return "skill";
		}
		for (HttpToolEntity tool : boundHttpTools) {
			if (tool.getToolName().equals(name)) {
				return "http";
			}
		}
		for (BoundMcpTool tool : boundMcpTools) {
			if (tool.exposedName().equals(name)) {
				return "mcp";
			}
		}
		return "tool";
	}

	private static String errorText(Exception ex) {
		if (ex instanceof ResponseStatusException status && status.getReason() != null && !status.getReason().isBlank()) {
			return status.getReason();
		}
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return ex.getClass().getSimpleName();
		}
		return message;
	}

	private static Set<String> httpToolNames(List<HttpToolEntity> tools) {
		Set<String> names = new LinkedHashSet<>();
		for (HttpToolEntity tool : tools) {
			if (tool.getToolName() != null && !tool.getToolName().isBlank()) {
				names.add(tool.getToolName());
			}
		}
		return names;
	}

}
