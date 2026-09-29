package com.agentplatform.hub.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class McpSse {

	record Event(String event, String data) {
	}

	private McpSse() {
	}

	static List<Event> parse(String body) {
		List<Event> events = new ArrayList<>();
		if (body == null || body.isBlank()) {
			return events;
		}
		String normalized = body.replace("\r\n", "\n").replace('\r', '\n');
		for (String block : normalized.split("\n\n")) {
			Event event = parseBlock(block);
			if (event != null) {
				events.add(event);
			}
		}
		return events;
	}

	static Event readEvent(BufferedReader reader) throws IOException {
		String event = "message";
		StringBuilder data = new StringBuilder();
		boolean any = false;
		String line;
		while ((line = reader.readLine()) != null) {
			any = true;
			if (line.isEmpty()) {
				return new Event(event, data.toString());
			}
			if (line.startsWith("event:")) {
				event = line.substring(6).trim();
			}
			else if (line.startsWith("data:")) {
				String rest = line.substring(5);
				if (rest.startsWith(" ")) {
					rest = rest.substring(1);
				}
				if (data.length() > 0) {
					data.append('\n');
				}
				data.append(rest);
			}
		}
		if (!any) {
			return null;
		}
		return new Event(event, data.toString());
	}

	static String endpoint(List<Event> events) {
		for (Event event : events) {
			if ("endpoint".equalsIgnoreCase(event.event()) && event.data() != null && !event.data().isBlank()) {
				return event.data().trim();
			}
		}
		return "";
	}

	static Map<String, Object> jsonRpc(List<Event> events, Object id) {
		String want = McpJson.idKey(id);
		Map<String, Object> last = null;
		for (Event event : events) {
			if (event.data() == null || event.data().isBlank()) {
				continue;
			}
			String trimmed = event.data().trim();
			if (!trimmed.startsWith("{")) {
				continue;
			}
			try {
				Map<String, Object> payload = McpJson.object(trimmed);
				if (!payload.containsKey("jsonrpc")) {
					continue;
				}
				last = payload;
				if (want.equals(McpJson.idKey(payload.get("id")))) {
					return payload;
				}
			}
			catch (RuntimeException ignored) {
				// skip non-json data frames
			}
		}
		return last;
	}

	static boolean looksLikeSse(String contentType, String body) {
		String type = contentType == null ? "" : contentType.toLowerCase();
		if (type.contains("text/event-stream")) {
			return true;
		}
		if (body == null) {
			return false;
		}
		String trimmed = body.stripLeading();
		return trimmed.startsWith("event:") || trimmed.startsWith("data:");
	}

	private static Event parseBlock(String block) {
		if (block == null || block.isBlank()) {
			return null;
		}
		String event = "message";
		StringBuilder data = new StringBuilder();
		for (String line : block.split("\n")) {
			if (line.startsWith("event:")) {
				event = line.substring(6).trim();
			}
			else if (line.startsWith("data:")) {
				String rest = line.substring(5);
				if (rest.startsWith(" ")) {
					rest = rest.substring(1);
				}
				if (data.length() > 0) {
					data.append('\n');
				}
				data.append(rest);
			}
		}
		if (data.length() == 0 && "message".equals(event)) {
			return null;
		}
		return new Event(event, data.toString());
	}

}
