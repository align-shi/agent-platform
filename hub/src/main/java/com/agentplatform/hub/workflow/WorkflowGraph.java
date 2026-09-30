package com.agentplatform.hub.workflow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class WorkflowGraph {

	public static final String START = "start";
	public static final String END = "end";
	public static final String AGENT = "agent";
	public static final String LLM = "llm";
	public static final String HTTP = "http";
	public static final String CONDITION = "condition";

	private static final Set<String> TYPES = Set.of(START, END, AGENT, LLM, HTTP, CONDITION);
	private static final Set<String> BRANCHES = Set.of("true", "false");

	private WorkflowGraph() {
	}

	public static WorkflowDtos.Graph blank() {
		return new WorkflowDtos.Graph(
				List.of(
						new WorkflowDtos.Node("start", START, 80, 180, Map.of()),
						new WorkflowDtos.Node("end", END, 420, 180, Map.of())),
				List.of(new WorkflowDtos.Edge("e-start-end", "start", "end", null)));
	}

	public static void validate(WorkflowDtos.Graph graph) {
		if (graph == null || graph.nodes() == null || graph.nodes().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工作流至少要有开始和结束");
		}
		if (graph.nodes().size() > 40) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工作流节点不能超过 40 个");
		}
		List<WorkflowDtos.Edge> edges = graph.edges() == null ? List.of() : graph.edges();
		if (edges.size() > 80) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工作流连线不能超过 80 条");
		}
		Map<String, WorkflowDtos.Node> nodes = new HashMap<>();
		int starts = 0;
		int ends = 0;
		for (WorkflowDtos.Node node : graph.nodes()) {
			if (node == null || node.id() == null || node.id().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点缺少 id");
			}
			if (node.id().length() > 64) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点 id 过长");
			}
			if (!TYPES.contains(typeOf(node))) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的节点类型：" + node.type());
			}
			if (nodes.put(node.id(), node) != null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点 id 重复：" + node.id());
			}
			if (START.equals(typeOf(node))) {
				starts++;
			}
			if (END.equals(typeOf(node))) {
				ends++;
			}
		}
		if (starts != 1 || ends != 1) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工作流必须恰好有一个开始和一个结束");
		}
		Map<String, List<WorkflowDtos.Edge>> outgoing = new HashMap<>();
		Map<String, Integer> incoming = new HashMap<>();
		for (WorkflowDtos.Node node : graph.nodes()) {
			outgoing.put(node.id(), new ArrayList<>());
			incoming.put(node.id(), 0);
		}
		Set<String> edgeIds = new HashSet<>();
		for (WorkflowDtos.Edge edge : edges) {
			if (edge == null || edge.id() == null || edge.id().isBlank()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "连线缺少 id");
			}
			if (!edgeIds.add(edge.id())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "连线 id 重复：" + edge.id());
			}
			if (!nodes.containsKey(edge.source()) || !nodes.containsKey(edge.target())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "连线指向了不存在的节点");
			}
			if (edge.source().equals(edge.target())) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点不能连到自己");
			}
			outgoing.get(edge.source()).add(edge);
			incoming.put(edge.target(), incoming.get(edge.target()) + 1);
		}
		String startId = null;
		for (WorkflowDtos.Node node : graph.nodes()) {
			String type = typeOf(node);
			List<WorkflowDtos.Edge> next = outgoing.get(node.id());
			int in = incoming.get(node.id());
			if (START.equals(type)) {
				startId = node.id();
				if (in != 0) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始节点不能有入线");
				}
				if (next.size() != 1 || branchOf(next.get(0)) != null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开始节点只能有一条普通出线");
				}
			}
			else if (END.equals(type)) {
				if (in < 1) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束节点需要有入线");
				}
				if (!next.isEmpty()) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结束节点不能有出线");
				}
			}
			else if (CONDITION.equals(type)) {
				if (in < 1) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "条件节点需要有入线");
				}
				if (next.size() != 2) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "条件节点必须连出「是」和「否」两条线");
				}
				Set<String> branches = new HashSet<>();
				for (WorkflowDtos.Edge edge : next) {
					String branch = branchOf(edge);
					if (branch == null || !BRANCHES.contains(branch) || !branches.add(branch)) {
						throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "条件节点必须分别连出 true 和 false");
					}
				}
			}
			else {
				if (in < 1) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点「" + node.id() + "」没有入线");
				}
				if (next.size() != 1 || branchOf(next.get(0)) != null) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "节点「" + node.id() + "」只能有一条普通出线");
				}
			}
		}
		if (hasCycle(startId, outgoing)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工作流不能有环");
		}
		Set<String> seen = new HashSet<>();
		ArrayDeque<String> queue = new ArrayDeque<>();
		queue.add(startId);
		seen.add(startId);
		while (!queue.isEmpty()) {
			String current = queue.removeFirst();
			for (WorkflowDtos.Edge edge : outgoing.get(current)) {
				if (seen.add(edge.target())) {
					queue.add(edge.target());
				}
			}
		}
		if (seen.size() != nodes.size()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "有节点没有从开始连到");
		}
	}

	public static String typeOf(WorkflowDtos.Node node) {
		return node.type() == null ? "" : node.type().trim().toLowerCase();
	}

	public static String branchOf(WorkflowDtos.Edge edge) {
		if (edge == null || edge.branch() == null || edge.branch().isBlank()) {
			return null;
		}
		return edge.branch().trim().toLowerCase();
	}

	public static WorkflowDtos.Node node(WorkflowDtos.Graph graph, String id) {
		return graph.nodes().stream()
				.filter((item) -> item.id().equals(id))
				.findFirst()
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "找不到节点 " + id));
	}

	public static List<WorkflowDtos.Edge> outgoing(WorkflowDtos.Graph graph, String id) {
		List<WorkflowDtos.Edge> edges = new ArrayList<>();
		if (graph.edges() == null) {
			return edges;
		}
		for (WorkflowDtos.Edge edge : graph.edges()) {
			if (id.equals(edge.source())) {
				edges.add(edge);
			}
		}
		return edges;
	}

	public static String field(Map<String, Object> data, String key) {
		if (data == null || data.get(key) == null) {
			return "";
		}
		return String.valueOf(data.get(key)).trim();
	}

	public static boolean matches(String text, Map<String, Object> data) {
		String operator = field(data, "operator");
		String expected = field(data, "value");
		String actual = text == null ? "" : text;
		if ("not_empty".equals(operator)) {
			return !actual.isBlank();
		}
		if ("equals".equals(operator)) {
			return actual.equals(expected);
		}
		return actual.contains(expected);
	}

	public static String render(String template, String input, String output) {
		String text = template == null ? "" : template;
		return text.replace("{{input}}", input == null ? "" : input).replace("{{output}}", output == null ? "" : output);
	}

	private static boolean hasCycle(String startId, Map<String, List<WorkflowDtos.Edge>> outgoing) {
		Set<String> visiting = new HashSet<>();
		Set<String> visited = new HashSet<>();
		return dfsCycle(startId, outgoing, visiting, visited);
	}

	private static boolean dfsCycle(
			String current,
			Map<String, List<WorkflowDtos.Edge>> outgoing,
			Set<String> visiting,
			Set<String> visited) {
		if (visited.contains(current)) {
			return false;
		}
		if (!visiting.add(current)) {
			return true;
		}
		for (WorkflowDtos.Edge edge : outgoing.getOrDefault(current, List.of())) {
			if (dfsCycle(edge.target(), outgoing, visiting, visited)) {
				return true;
			}
		}
		visiting.remove(current);
		visited.add(current);
		return false;
	}

}
