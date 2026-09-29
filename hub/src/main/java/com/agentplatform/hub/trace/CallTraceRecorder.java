package com.agentplatform.hub.trace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CallTraceRecorder {

	private final String id = UUID.randomUUID().toString();
	private final long originNanos = System.nanoTime();
	private final Instant startedAt = Instant.now();
	private final String agentId;
	private final String agentName;
	private final String providerId;
	private final String model;
	private final String conversationId;
	private final String userInput;
	private final List<Node> roots = new ArrayList<>();
	private Node round;
	private int rounds;
	private int modelCalls;
	private int toolCalls;
	private int mcpCalls;
	private int contexts;
	private int promptTokens;
	private int completionTokens;
	private int totalTokens;
	private int sequence;

	private CallTraceRecorder(
			String agentId,
			String agentName,
			String providerId,
			String model,
			String conversationId,
			String userInput) {
		this.agentId = agentId;
		this.agentName = agentName == null || agentName.isBlank() ? "智能体" : agentName;
		this.providerId = providerId;
		this.model = model == null ? "" : model;
		this.conversationId = conversationId;
		this.userInput = userInput == null ? "" : userInput;
	}

	public static CallTraceRecorder start(
			String agentId,
			String agentName,
			String providerId,
			String model,
			String conversationId,
			String userInput) {
		return new CallTraceRecorder(agentId, agentName, providerId, model, conversationId, userInput);
	}

	public long mark() {
		return System.nanoTime();
	}

	public void systemPrompt(String prompt) {
		String text = prompt == null ? "" : prompt;
		Node node = node("system", "初始系统提示词", brief(text), 0, 0, "ok");
		node.sections.add(section("prompt", "提示词", text.isBlank() ? "没有配置系统提示词。" : text));
		roots.add(node);
	}

	public void retrieval(String text, long mark) {
		if (text == null || text.isBlank()) {
			return;
		}
		Node node = node("context", "知识库检索", brief(text), offset(mark), elapsed(mark), "ok");
		node.sections.add(section("result", "检索结果", text));
		roots.add(node);
	}

	public void beginRound() {
		rounds++;
		round = node("round", "第 " + rounds + " 轮", "", offset(originNanos), 0, "ok");
		roots.add(round);
		if (rounds == 1) {
			Node input = node("input", "第 1 轮 · 消息", brief(userInput), offset(originNanos), 0, "ok");
			input.sections.add(section("message", "消息", userInput.isBlank() ? "空消息" : userInput));
			round.children.add(input);
		}
	}

	public void context(
			String system,
			String skills,
			String toolDefinitions,
			String history,
			String knowledge,
			long mark) {
		Node parent = round();
		contexts++;
		String title = contexts == 1
				? "第 " + rounds + " 轮 · LLM 上下文"
				: "第 " + rounds + " 轮 · LLM 上下文 #" + contexts;
		List<String> parts = new ArrayList<>();
		if (skills != null && !skills.isBlank()) {
			parts.add("Skills 正文");
		}
		if (toolDefinitions != null && !toolDefinitions.isBlank()) {
			parts.add("工具定义");
		}
		if (history != null && !history.isBlank()) {
			parts.add("对话历史");
		}
		if (knowledge != null && !knowledge.isBlank()) {
			parts.add("知识库");
		}
		String summary = parts.isEmpty() ? "组装后的上下文" : String.join(" · ", parts);
		Node node = node("context", title, summary, offset(mark), elapsed(mark), "ok");
		node.sections.add(section("overview", "概述", """
				来源：LLM-context
				状态：已完成
				摘要：%s
				""".formatted(summary).strip()));
		node.sections.add(section("prompt", "组装提示词", blank(system, "这一轮没有系统提示词。")));
		node.sections.add(section("skills", "Skills", blank(skills, "这一轮没有技能。")));
		node.sections.add(section("tools", "工具定义", blank(toolDefinitions, "这一轮没有向模型提供工具。")));
		node.sections.add(section("history", "对话历史", blank(history, "没有对话历史。")));
		if (knowledge != null && !knowledge.isBlank()) {
			node.sections.add(section("knowledge", "知识库", knowledge));
		}
		parent.children.add(node);
	}

	public void model(
			String modelName,
			String output,
			int prompt,
			int completion,
			int total,
			int toolsOffered,
			long mark) {
		promptTokens += Math.max(0, prompt);
		completionTokens += Math.max(0, completion);
		totalTokens += Math.max(0, total);
		addModel(modelName, output, prompt, completion, total, toolsOffered, mark, "ok", "");
	}

	public void modelFailed(String modelName, String error, int toolsOffered, long mark) {
		addModel(modelName, "", 0, 0, 0, toolsOffered, mark, "error", error == null ? "" : error);
	}

	public void tool(String name, String kind, String arguments, String output, long mark) {
		toolCalls++;
		if ("mcp".equals(kind)) {
			mcpCalls++;
		}
		String type = switch (kind == null ? "" : kind) {
			case "mcp" -> "MCP";
			case "http" -> "HTTP";
			case "skill" -> "技能";
			default -> "工具";
		};
		boolean failed = output != null && output.startsWith("ERROR:");
		String args = arguments == null || arguments.isBlank() ? "{}" : arguments;
		Node node = node(
				"tool",
				"第 " + Math.max(rounds, 1) + " 轮 · " + (name == null ? "工具" : name),
				type + " · " + brief(args),
				offset(mark),
				elapsed(mark),
				failed ? "error" : "ok");
		node.sections.add(section("overview", "概述", """
				类型：%s
				名称：%s
				状态：%s
				耗时：%d 毫秒
				""".formatted(type, name == null ? "" : name, failed ? "失败" : "已完成", node.durationMs).strip()));
		node.sections.add(section("arguments", "参数", args));
		node.sections.add(section("output", "结果", output == null || output.isBlank() ? "空结果" : output));
		round().children.add(node);
	}

	public void assistant(String text) {
		if (text == null || text.isBlank()) {
			return;
		}
		Node node = node(
				"assistant",
				"第 " + Math.max(rounds, 1) + " 轮 · 回复",
				brief(text),
				offset(System.nanoTime()),
				0,
				"ok");
		node.sections.add(section("reply", "回复", text));
		round().children.add(node);
	}

	public Draft success() {
		return finish("SUCCESS", "");
	}

	public Draft failure(String error) {
		if (round != null) {
			round.status = "error";
		}
		return finish("ERROR", error == null ? "" : error);
	}

	private void addModel(
			String modelName,
			String output,
			int prompt,
			int completion,
			int total,
			int toolsOffered,
			long mark,
			String status,
			String error) {
		modelCalls++;
		String resolvedModel = modelName == null || modelName.isBlank() ? this.model : modelName;
		String usage = total > 0
				? "输入 " + prompt + " / 输出 " + completion + " / 合计 " + total
				: "上游没有返回 token 用量";
		String summary = resolvedModel
				+ (toolsOffered > 0 ? " · " + toolsOffered + " 个工具" : "")
				+ (total > 0 ? " · " + total + " token" : "");
		Node node = node(
				"model",
				"请求 #" + modelCalls + " 第 " + Math.max(rounds, 1) + " 轮",
				summary,
				offset(mark),
				elapsed(mark),
				status);
		String overview = """
				模型：%s
				状态：%s
				耗时：%d 毫秒
				提供的工具：%d
				%s
				""".formatted(
				resolvedModel,
				"error".equals(status) ? "失败" : "已完成",
				node.durationMs,
				Math.max(0, toolsOffered),
				usage).strip();
		if (error != null && !error.isBlank()) {
			overview = overview + "\n错误：" + error.strip();
		}
		node.sections.add(section("overview", "概述", overview));
		String body = output == null ? "" : output;
		node.sections.add(section(
				"output",
				"输出",
				body.isBlank() ? "这一轮没有正文。" : body));
		round().children.add(node);
	}

	private Draft finish(String status, String error) {
		if (round == null) {
			beginRound();
		}
		for (Node root : roots) {
			seal(root);
		}
		List<CallDtos.Span> spans = new ArrayList<>();
		for (Node root : roots) {
			spans.add(toSpan(root));
		}
		int total = totalTokens > 0 ? totalTokens : promptTokens + completionTokens;
		return new Draft(
				id,
				agentId,
				agentName,
				conversationId,
				providerId,
				model,
				status,
				userInput,
				startedAt,
				Math.max(0, elapsed(originNanos)),
				rounds,
				modelCalls,
				toolCalls,
				mcpCalls,
				promptTokens,
				completionTokens,
				total,
				error,
				CallTraceJson.encode(spans));
	}

	private Node round() {
		if (round == null) {
			beginRound();
		}
		return round;
	}

	private void seal(Node node) {
		for (Node child : node.children) {
			seal(child);
		}
		if (!"round".equals(node.kind) || node.children.isEmpty()) {
			return;
		}
		long start = node.children.get(0).offsetMs;
		long end = start;
		int models = 0;
		int tools = 0;
		boolean failed = "error".equals(node.status);
		for (Node child : node.children) {
			start = Math.min(start, child.offsetMs);
			end = Math.max(end, child.offsetMs + child.durationMs);
			if ("model".equals(child.kind)) {
				models++;
			}
			if ("tool".equals(child.kind)) {
				tools++;
			}
			if ("error".equals(child.status)) {
				failed = true;
			}
		}
		node.offsetMs = start;
		node.durationMs = Math.max(0, end - start);
		node.status = failed ? "error" : "ok";
		node.summary = "模型 " + models + " · 工具 " + tools;
		if (node.sections.isEmpty()) {
			node.sections.add(section("overview", "概述", """
					状态：%s
					模型调用：%d
					工具调用：%d
					耗时：%d 毫秒
					""".formatted(failed ? "失败" : "已完成", models, tools, node.durationMs).strip()));
		}
	}

	private CallDtos.Span toSpan(Node node) {
		List<CallDtos.Span> children = new ArrayList<>();
		for (Node child : node.children) {
			children.add(toSpan(child));
		}
		return new CallDtos.Span(
				node.id,
				node.kind,
				node.title,
				node.summary,
				node.offsetMs,
				node.durationMs,
				node.status,
				List.copyOf(node.sections),
				List.copyOf(children));
	}

	private Node node(String kind, String title, String summary, long offsetMs, long durationMs, String status) {
		Node node = new Node();
		node.id = "s" + (++sequence);
		node.kind = kind;
		node.title = title;
		node.summary = summary == null ? "" : summary;
		node.offsetMs = Math.max(0, offsetMs);
		node.durationMs = Math.max(0, durationMs);
		node.status = status == null || status.isBlank() ? "ok" : status;
		return node;
	}

	private CallDtos.Section section(String key, String label, String text) {
		return new CallDtos.Section(key, label, text == null ? "" : text);
	}

	private long offset(long mark) {
		return Math.max(0, (mark - originNanos) / 1_000_000L);
	}

	private long elapsed(long mark) {
		return Math.max(0, (System.nanoTime() - mark) / 1_000_000L);
	}

	private static String blank(String text, String fallback) {
		return text == null || text.isBlank() ? fallback : text;
	}

	private static String brief(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String line = text.strip().lines().findFirst().orElse("").trim();
		if (line.length() <= 80) {
			return line;
		}
		return line.substring(0, 80);
	}

	public record Draft(
			String id,
			String agentId,
			String agentName,
			String conversationId,
			String providerId,
			String model,
			String status,
			String userInput,
			Instant startedAt,
			long latencyMs,
			int rounds,
			int modelCalls,
			int toolCalls,
			int mcpCalls,
			int promptTokens,
			int completionTokens,
			int totalTokens,
			String error,
			String traceJson) {
	}

	private static final class Node {
		private String id;
		private String kind;
		private String title;
		private String summary;
		private long offsetMs;
		private long durationMs;
		private String status;
		private final List<CallDtos.Section> sections = new ArrayList<>();
		private final List<Node> children = new ArrayList<>();
	}

}
