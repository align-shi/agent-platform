package com.agentplatform.hub.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class McpJson {

	private McpJson() {
	}

	static String write(Object value) {
		StringBuilder json = new StringBuilder();
		write(json, value);
		return json.toString();
	}

	static Object read(String json) {
		if (json == null || json.isBlank()) {
			return null;
		}
		Parser parser = new Parser(json.trim());
		Object value = parser.value();
		parser.skipWs();
		if (parser.pos < parser.text.length()) {
			throw new IllegalArgumentException("JSON 多余内容");
		}
		return value;
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> object(String json) {
		Object value = read(json);
		if (value == null) {
			return new LinkedHashMap<>();
		}
		if (value instanceof Map<?, ?> map) {
			return (Map<String, Object>) map;
		}
		throw new IllegalArgumentException("JSON 不是对象");
	}

	@SuppressWarnings("unchecked")
	static List<Object> array(String json) {
		Object value = read(json);
		if (value == null) {
			return new ArrayList<>();
		}
		if (value instanceof List<?> list) {
			return (List<Object>) list;
		}
		throw new IllegalArgumentException("JSON 不是数组");
	}

	static String idKey(Object id) {
		if (id instanceof Number number) {
			if (number.doubleValue() == number.longValue()) {
				return Long.toString(number.longValue());
			}
			return number.toString();
		}
		return id == null ? "" : String.valueOf(id);
	}

	private static void write(StringBuilder json, Object value) {
		if (value == null) {
			json.append("null");
			return;
		}
		if (value instanceof String text) {
			json.append(quote(text));
			return;
		}
		if (value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Short
				|| value instanceof Byte) {
			json.append(value);
			return;
		}
		if (value instanceof Double number) {
			if (number.isNaN() || number.isInfinite()) {
				json.append("null");
				return;
			}
			json.append(number);
			return;
		}
		if (value instanceof Float number) {
			if (number.isNaN() || number.isInfinite()) {
				json.append("null");
				return;
			}
			json.append(number);
			return;
		}
		if (value instanceof Number number) {
			json.append(number);
			return;
		}
		if (value instanceof Map<?, ?> map) {
			json.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!first) {
					json.append(',');
				}
				first = false;
				json.append(quote(String.valueOf(entry.getKey()))).append(':');
				write(json, entry.getValue());
			}
			json.append('}');
			return;
		}
		if (value instanceof Iterable<?> items) {
			json.append('[');
			boolean first = true;
			for (Object item : items) {
				if (!first) {
					json.append(',');
				}
				first = false;
				write(json, item);
			}
			json.append(']');
			return;
		}
		json.append(quote(String.valueOf(value)));
	}

	static String quote(String value) {
		if (value == null) {
			return "\"\"";
		}
		StringBuilder json = new StringBuilder("\"");
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			switch (ch) {
				case '"' -> json.append("\\\"");
				case '\\' -> json.append("\\\\");
				case '\n' -> json.append("\\n");
				case '\r' -> json.append("\\r");
				case '\t' -> json.append("\\t");
				default -> {
					if (ch < 0x20) {
						json.append(String.format("\\u%04x", (int) ch));
					}
					else {
						json.append(ch);
					}
				}
			}
		}
		return json.append('"').toString();
	}

	private static final class Parser {
		private final String text;
		private int pos;

		private Parser(String text) {
			this.text = text;
		}

		private Object value() {
			skipWs();
			if (pos >= text.length()) {
				throw new IllegalArgumentException("JSON 不完整");
			}
			char ch = text.charAt(pos);
			if (ch == '{') {
				return object();
			}
			if (ch == '[') {
				return array();
			}
			if (ch == '"') {
				return string();
			}
			if (ch == 't' || ch == 'f') {
				return bool();
			}
			if (ch == 'n') {
				return nul();
			}
			if (ch == '-' || (ch >= '0' && ch <= '9')) {
				return number();
			}
			throw new IllegalArgumentException("JSON 无法解析: " + ch);
		}

		private Map<String, Object> object() {
			expect('{');
			Map<String, Object> map = new LinkedHashMap<>();
			skipWs();
			if (peek('}')) {
				pos++;
				return map;
			}
			while (true) {
				skipWs();
				String key = string();
				skipWs();
				expect(':');
				map.put(key, value());
				skipWs();
				if (peek('}')) {
					pos++;
					return map;
				}
				expect(',');
			}
		}

		private List<Object> array() {
			expect('[');
			List<Object> list = new ArrayList<>();
			skipWs();
			if (peek(']')) {
				pos++;
				return list;
			}
			while (true) {
				list.add(value());
				skipWs();
				if (peek(']')) {
					pos++;
					return list;
				}
				expect(',');
			}
		}

		private String string() {
			expect('"');
			StringBuilder value = new StringBuilder();
			while (pos < text.length()) {
				char ch = text.charAt(pos++);
				if (ch == '"') {
					return value.toString();
				}
				if (ch != '\\') {
					value.append(ch);
					continue;
				}
				if (pos >= text.length()) {
					throw new IllegalArgumentException("JSON 字符串未结束");
				}
				char esc = text.charAt(pos++);
				switch (esc) {
					case '"' -> value.append('"');
					case '\\' -> value.append('\\');
					case '/' -> value.append('/');
					case 'b' -> value.append('\b');
					case 'f' -> value.append('\f');
					case 'n' -> value.append('\n');
					case 'r' -> value.append('\r');
					case 't' -> value.append('\t');
					case 'u' -> {
						if (pos + 4 > text.length()) {
							throw new IllegalArgumentException("JSON unicode 不完整");
						}
						value.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
						pos += 4;
					}
					default -> throw new IllegalArgumentException("JSON 转义不合法: " + esc);
				}
			}
			throw new IllegalArgumentException("JSON 字符串未结束");
		}

		private Boolean bool() {
			if (match("true")) {
				return Boolean.TRUE;
			}
			if (match("false")) {
				return Boolean.FALSE;
			}
			throw new IllegalArgumentException("JSON 布尔值不合法");
		}

		private Object nul() {
			if (match("null")) {
				return null;
			}
			throw new IllegalArgumentException("JSON null 不合法");
		}

		private Number number() {
			int start = pos;
			if (peek('-')) {
				pos++;
			}
			while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
				pos++;
			}
			boolean fraction = false;
			if (peek('.')) {
				fraction = true;
				pos++;
				while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
					pos++;
				}
			}
			if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
				fraction = true;
				pos++;
				if (peek('+') || peek('-')) {
					pos++;
				}
				while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
					pos++;
				}
			}
			String raw = text.substring(start, pos);
			if (fraction) {
				return Double.parseDouble(raw);
			}
			try {
				long value = Long.parseLong(raw);
				if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
					return (int) value;
				}
				return value;
			}
			catch (NumberFormatException ex) {
				return Double.parseDouble(raw);
			}
		}

		private void skipWs() {
			while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
				pos++;
			}
		}

		private boolean peek(char ch) {
			return pos < text.length() && text.charAt(pos) == ch;
		}

		private void expect(char ch) {
			skipWs();
			if (!peek(ch)) {
				throw new IllegalArgumentException("JSON 缺少 " + ch);
			}
			pos++;
		}

		private boolean match(String token) {
			if (text.startsWith(token, pos)) {
				pos += token.length();
				return true;
			}
			return false;
		}
	}

}
