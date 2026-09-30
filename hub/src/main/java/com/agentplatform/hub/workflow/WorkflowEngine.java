package com.agentplatform.hub.workflow;

import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.agentplatform.hub.agent.AgentEntity;
import com.agentplatform.hub.agent.AgentService;
import com.agentplatform.hub.chat.AgentChatService;
import com.agentplatform.hub.chat.ChatDtos;
import com.agentplatform.hub.chat.ChatProxyService;
import com.agentplatform.hub.conversation.ConversationService;
import com.agentplatform.hub.httptool.HttpToolEntity;
import com.agentplatform.hub.httptool.HttpToolService;
import com.agentplatform.hub.provider.ProviderEntity;
import com.agentplatform.hub.provider.ProviderService;
import com.agentplatform.hub.provider.ProviderType;

@Service
public class WorkflowEngine {

	private static final int MAX_STEPS = 32;

	private final AgentService agents;
	private final AgentChatService agentChat;
	private final ConversationService conversations;
	private final HttpToolService httpTools;
	private final ProviderService providers;
	private final ChatProxyService chatProxy;

	public WorkflowEngine(
			AgentService agents,
			@Lazy AgentChatService agentChat,
			ConversationService conversations,
			HttpToolService httpTools,
			ProviderService providers,
			ChatProxyService chatProxy) {
		this.agents = agents;
		this.agentChat = agentChat;
		this.conversations = conversations;
		this.httpTools = httpTools;
		this.providers = providers;
		this.chatProxy = chatProxy;
	}

	public Result execute(WorkflowDtos.Graph graph, String input) {
		WorkflowGraph.validate(graph);
		String text = input == null ? "" : input;
		String output = text;
		String current = WorkflowGraph.node(graph, startId(graph)).id();
		List<WorkflowDtos.StepView> steps = new ArrayList<>();
		for (int i = 0; i < MAX_STEPS; i++) {
			WorkflowDtos.Node node = WorkflowGraph.node(graph, current);
			String type = WorkflowGraph.typeOf(node);
			try {
				StepResult step = runNode(node, text, output);
				steps.add(new WorkflowDtos.StepView(node.id(), type, "succeeded", step.input(), step.output(), null));
				output = step.output();
				if (WorkflowGraph.END.equals(type)) {
					return new Result("succeeded", output, null, steps);
				}
				current = next(graph, node, output);
			}
			catch (Exception ex) {
				String reason = userFacing(ex);
				steps.add(new WorkflowDtos.StepView(node.id(), type, "failed", text, output, reason));
				return new Result("failed", output, reason, steps);
			}
		}
		return new Result("failed", output, "工作流步数过多", steps);
	}

	private StepResult runNode(WorkflowDtos.Node node, String input, String output) throws Exception {
		return switch (WorkflowGraph.typeOf(node)) {
			case WorkflowGraph.START -> new StepResult(input, input);
			case WorkflowGraph.END -> new StepResult(output, output);
			case WorkflowGraph.AGENT -> runAgent(node, input, output);
			case WorkflowGraph.LLM -> runLlm(node, input, output);
			case WorkflowGraph.HTTP -> runHttp(node, input, output);
			case WorkflowGraph.CONDITION -> {
				boolean matched = WorkflowGraph.matches(pick(node, input, output), node.data());
				yield new StepResult(pick(node, input, output), matched ? "true" : "false");
			}
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的节点类型");
		};
	}

	private StepResult runAgent(WorkflowDtos.Node node, String input, String output) throws Exception {
		String agentId = WorkflowGraph.field(node.data(), "agentId");
		if (agentId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "智能体节点还没有选择智能体");
		}
		AgentEntity agent = agents.require(agentId);
		if (agent.getCode() == null || agent.getCode().isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这个智能体还没有唯一编码");
		}
		String prompt = WorkflowGraph.field(node.data(), "prompt");
		if (prompt.isBlank()) {
			prompt = "{{input}}";
		}
		String content = WorkflowGraph.render(prompt, input, output);
		String conversationId = conversations.getOrCreate(agent.getId(), null).getId();
		ChatDtos.Reply reply = agentChat.completeByCode(agent.getCode(), conversationId, content);
		return new StepResult(content, reply.content() == null ? "" : reply.content());
	}

	private StepResult runLlm(WorkflowDtos.Node node, String input, String output) {
		String providerId = WorkflowGraph.field(node.data(), "providerId");
		if (providerId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大模型节点还没有选择提供商");
		}
		ProviderEntity provider = providers.require(providerId);
		if (provider.getType() != ProviderType.CHAT) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大模型节点只能使用对话模型");
		}
		String model = WorkflowGraph.field(node.data(), "model");
		if (model.isBlank()) {
			model = provider.getDefaultModel() == null ? "" : provider.getDefaultModel().trim();
		}
		if (model.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大模型节点还没有填写模型");
		}
		String prompt = WorkflowGraph.field(node.data(), "prompt");
		if (prompt.isBlank()) {
			prompt = "{{input}}";
		}
		String user = WorkflowGraph.render(prompt, input, output);
		String system = WorkflowGraph.field(node.data(), "systemPrompt");
		if (system.isBlank()) {
			system = "按用户提示直接回答，不要追问。";
		}
		String reply = chatProxy.completeText(provider, providers.decryptApiKey(provider), model, system, user);
		return new StepResult(user, reply);
	}

	private StepResult runHttp(WorkflowDtos.Node node, String input, String output) {
		String connectorId = WorkflowGraph.field(node.data(), "connectorId");
		String toolId = WorkflowGraph.field(node.data(), "toolId");
		if (connectorId.isBlank() || toolId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HTTP 节点还没有选择工具");
		}
		HttpToolEntity tool = httpTools.requireTool(connectorId, toolId);
		String template = WorkflowGraph.field(node.data(), "argumentsJson");
		if (template.isBlank()) {
			template = "{}";
		}
		String arguments = WorkflowGraph.render(template, jsonEscape(input), jsonEscape(output));
		return new StepResult(arguments, httpTools.invoke(tool, arguments));
	}

	private static String next(WorkflowDtos.Graph graph, WorkflowDtos.Node node, String output) {
		List<WorkflowDtos.Edge> edges = WorkflowGraph.outgoing(graph, node.id());
		if (WorkflowGraph.CONDITION.equals(WorkflowGraph.typeOf(node))) {
			String wanted = "true".equals(output) ? "true" : "false";
			for (WorkflowDtos.Edge edge : edges) {
				if (wanted.equals(WorkflowGraph.branchOf(edge))) {
					return edge.target();
				}
			}
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "条件节点缺少 " + wanted + " 分支");
		}
		if (edges.size() != 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点没有下一跳");
		}
		return edges.get(0).target();
	}

	private static String startId(WorkflowDtos.Graph graph) {
		return graph.nodes().stream()
				.filter((node) -> WorkflowGraph.START.equals(WorkflowGraph.typeOf(node)))
				.findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少开始节点"))
				.id();
	}

	private static String pick(WorkflowDtos.Node node, String input, String output) {
		return "input".equals(WorkflowGraph.field(node.data(), "source")) ? input : output;
	}

	private static String jsonEscape(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
	}

	private static String userFacing(Exception ex) {
		if (ex instanceof ResponseStatusException status && status.getReason() != null && !status.getReason().isBlank()) {
			return status.getReason();
		}
		return ex.getMessage() == null || ex.getMessage().isBlank() ? "节点执行失败" : ex.getMessage();
	}

	public record Result(String status, String output, String error, List<WorkflowDtos.StepView> steps) {
	}

	private record StepResult(String input, String output) {
	}

}
