package com.agentplatform.hub.conversation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.json.JsonParserFactory;

public final class CitationJson {

	private CitationJson() {
	}

	public static String encode(List<Item> items) {
		if (items == null || items.isEmpty()) {
			return "";
		}
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < items.size(); i++) {
			if (i > 0) {
				json.append(',');
			}
			Item item = items.get(i);
			json.append("{\"knowledgeBase\":").append(quote(item.knowledgeBase()))
					.append(",\"document\":").append(quote(item.document()))
					.append(",\"content\":").append(quote(item.content()))
					.append('}');
		}
		return json.append(']').toString();
	}

	static List<ConversationDtos.CitationView> decode(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			List<Object> rows = JsonParserFactory.getJsonParser().parseList(json);
			List<ConversationDtos.CitationView> citations = new ArrayList<>();
			for (Object row : rows) {
				if (!(row instanceof Map<?, ?> map)) {
					continue;
				}
				citations.add(new ConversationDtos.CitationView(
						text(map.get("knowledgeBase")),
						text(map.get("document")),
						text(map.get("content"))));
			}
			return citations;
		}
		catch (RuntimeException ex) {
			return List.of();
		}
	}

	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static String quote(String value) {
		StringBuilder out = new StringBuilder("\"");
		String text = value == null ? "" : value;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			switch (ch) {
				case '\\' -> out.append("\\\\");
				case '"' -> out.append("\\\"");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (ch < 0x20) {
						out.append(String.format("\\u%04x", (int) ch));
					}
					else {
						out.append(ch);
					}
				}
			}
		}
		return out.append('"').toString();
	}

	public record Item(String knowledgeBase, String document, String content) {
	}

}
