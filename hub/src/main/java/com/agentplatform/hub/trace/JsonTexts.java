package com.agentplatform.hub.trace;

import java.util.List;
import java.util.Map;

final class JsonTexts {

	private JsonTexts() {
	}

	static String pretty(Object value) {
		StringBuilder out = new StringBuilder();
		write(out, value, 0);
		return out.toString();
	}

	private static void write(StringBuilder out, Object value, int indent) {
		if (value == null) {
			out.append("null");
			return;
		}
		if (value instanceof String text) {
			out.append(quote(text));
			return;
		}
		if (value instanceof Number || value instanceof Boolean) {
			out.append(value);
			return;
		}
		if (value instanceof Map<?, ?> map) {
			if (map.isEmpty()) {
				out.append("{}");
				return;
			}
			out.append("{\n");
			int index = 0;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (index++ > 0) {
					out.append(",\n");
				}
				pad(out, indent + 1);
				out.append(quote(String.valueOf(entry.getKey()))).append(": ");
				write(out, entry.getValue(), indent + 1);
			}
			out.append('\n');
			pad(out, indent);
			out.append('}');
			return;
		}
		if (value instanceof List<?> list) {
			if (list.isEmpty()) {
				out.append("[]");
				return;
			}
			out.append("[\n");
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					out.append(",\n");
				}
				pad(out, indent + 1);
				write(out, list.get(i), indent + 1);
			}
			out.append('\n');
			pad(out, indent);
			out.append(']');
			return;
		}
		out.append(quote(String.valueOf(value)));
	}

	private static void pad(StringBuilder out, int indent) {
		out.append("  ".repeat(Math.max(0, indent)));
	}

	static String quote(String value) {
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

}
