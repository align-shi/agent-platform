package com.agentplatform.hub.trace;

import java.util.List;
import java.util.Map;

import com.agentplatform.hub.conversation.ConversationDtos;
import com.agentplatform.hub.knowledge.KnowledgeService;

public final class TraceTexts {

	private TraceTexts() {
	}

	public static String transcript(List<ConversationDtos.MessageView> messages) {
		if (messages == null || messages.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (ConversationDtos.MessageView message : messages) {
			appendTurn(out, message.role(), null, message.content(), null);
		}
		return out.toString();
	}

	public static String messages(List<Map<String, Object>> messages) {
		if (messages == null || messages.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		for (Map<String, Object> message : messages) {
			Object role = message.get("role");
			if ("system".equals(role)) {
				continue;
			}
			String name = message.get("name") == null ? null : String.valueOf(message.get("name"));
			String content = message.get("content") == null ? "" : String.valueOf(message.get("content"));
			appendTurn(out, role == null ? "" : String.valueOf(role), name, content, message.get("tool_calls"));
		}
		return out.toString();
	}

	public static String systemOf(List<Map<String, Object>> messages) {
		if (messages == null || messages.isEmpty()) {
			return "";
		}
		Map<String, Object> first = messages.get(0);
		if (!"system".equals(first.get("role")) || first.get("content") == null) {
			return "";
		}
		return String.valueOf(first.get("content"));
	}

	public static String tools(List<Map<String, Object>> definitions) {
		if (definitions == null || definitions.isEmpty()) {
			return "";
		}
		return JsonTexts.pretty(definitions);
	}

	public static String knowledge(KnowledgeService.Retrieval retrieval) {
		if (retrieval == null || !retrieval.searched()) {
			return "";
		}
		if (retrieval.hits().isEmpty()) {
			return "已检索，没有命中。";
		}
		StringBuilder out = new StringBuilder();
		for (KnowledgeService.Hit hit : retrieval.hits()) {
			if (out.length() > 0) {
				out.append("\n\n");
			}
			out.append(hit.knowledgeBase()).append(" / ").append(hit.document()).append('\n').append(hit.content());
		}
		return out.toString();
	}

	private static void appendTurn(StringBuilder out, String role, String name, String content, Object toolCalls) {
		if (out.length() > 0) {
			out.append("\n\n");
		}
		out.append('[').append(roleLabel(role)).append(']');
		if (name != null && !name.isBlank()) {
			out.append(' ').append(name);
		}
		out.append('\n');
		if (content != null && !content.isBlank()) {
			out.append(content);
		}
		if (toolCalls instanceof List<?> list && !list.isEmpty()) {
			if (content != null && !content.isBlank()) {
				out.append('\n');
			}
			out.append("工具调用：\n").append(JsonTexts.pretty(toolCalls));
		}
	}

	private static String roleLabel(String role) {
		return switch (role) {
			case "user" -> "用户";
			case "assistant" -> "助手";
			case "tool" -> "工具";
			case "system" -> "系统";
			default -> role == null || role.isBlank() ? "消息" : role;
		};
	}

}
