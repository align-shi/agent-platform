package com.agentplatform.hub.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.json.JsonParserFactory;

final class CallTraceJson {

	private static final int MAX_TEXT = 80_000;

	private CallTraceJson() {
	}

	static String encode(List<CallDtos.Span> spans) {
		StringBuilder json = new StringBuilder("{\"spans\":");
		writeSpans(json, spans);
		return json.append('}').toString();
	}

	static List<CallDtos.Span> decode(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(json);
			return spans(root.get("spans"));
		}
		catch (RuntimeException ex) {
			return List.of();
		}
	}

	private static void writeSpans(StringBuilder json, List<CallDtos.Span> spans) {
		json.append('[');
		if (spans != null) {
			for (int i = 0; i < spans.size(); i++) {
				if (i > 0) {
					json.append(',');
				}
				writeSpan(json, spans.get(i));
			}
		}
		json.append(']');
	}

	private static void writeSpan(StringBuilder json, CallDtos.Span span) {
		json.append("{\"id\":").append(JsonTexts.quote(span.id()))
				.append(",\"kind\":").append(JsonTexts.quote(span.kind()))
				.append(",\"title\":").append(JsonTexts.quote(span.title()))
				.append(",\"summary\":").append(JsonTexts.quote(clip(span.summary())))
				.append(",\"offsetMs\":").append(span.offsetMs())
				.append(",\"durationMs\":").append(span.durationMs())
				.append(",\"status\":").append(JsonTexts.quote(span.status()))
				.append(",\"sections\":[");
		List<CallDtos.Section> sections = span.sections() == null ? List.of() : span.sections();
		for (int i = 0; i < sections.size(); i++) {
			if (i > 0) {
				json.append(',');
			}
			CallDtos.Section section = sections.get(i);
			json.append("{\"key\":").append(JsonTexts.quote(section.key()))
					.append(",\"label\":").append(JsonTexts.quote(section.label()))
					.append(",\"text\":").append(JsonTexts.quote(clip(section.text())))
					.append('}');
		}
		json.append("],\"children\":");
		writeSpans(json, span.children());
		json.append('}');
	}

	private static List<CallDtos.Span> spans(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		List<CallDtos.Span> spans = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof Map<?, ?> map) {
				spans.add(span(map));
			}
		}
		return List.copyOf(spans);
	}

	private static CallDtos.Span span(Map<?, ?> map) {
		return new CallDtos.Span(
				text(map.get("id")),
				text(map.get("kind")),
				text(map.get("title")),
				text(map.get("summary")),
				number(map.get("offsetMs")),
				number(map.get("durationMs")),
				text(map.get("status")),
				sections(map.get("sections")),
				spans(map.get("children")));
	}

	private static List<CallDtos.Section> sections(Object raw) {
		if (!(raw instanceof List<?> list)) {
			return List.of();
		}
		List<CallDtos.Section> sections = new ArrayList<>();
		for (Object item : list) {
			if (item instanceof Map<?, ?> map) {
				sections.add(new CallDtos.Section(text(map.get("key")), text(map.get("label")), text(map.get("text"))));
			}
		}
		return List.copyOf(sections);
	}

	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static long number(Object value) {
		return value instanceof Number number ? number.longValue() : 0L;
	}

	private static String clip(String text) {
		if (text == null) {
			return "";
		}
		if (text.length() <= MAX_TEXT) {
			return text;
		}
		return text.substring(0, MAX_TEXT) + "\n…已截断";
	}

}
