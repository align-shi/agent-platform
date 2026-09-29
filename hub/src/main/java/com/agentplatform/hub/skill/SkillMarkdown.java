package com.agentplatform.hub.skill;

final class SkillMarkdown {

	private SkillMarkdown() {
	}

	static SkillView parse(String id, String raw) {
		String name = id;
		String description = "";
		String body = raw == null ? "" : raw.trim();
		if (raw != null && raw.startsWith("---")) {
			int end = raw.indexOf("\n---", 3);
			if (end > 0) {
				String front = raw.substring(3, end).trim();
				body = raw.substring(end + 4).trim();
				for (String line : front.split("\\R")) {
					int colon = line.indexOf(':');
					if (colon <= 0) {
						continue;
					}
					String key = line.substring(0, colon).trim();
					String value = stripQuotes(line.substring(colon + 1).trim());
					if ("name".equals(key) && !value.isBlank()) {
						name = value;
					}
					else if ("description".equals(key)) {
						description = value;
					}
				}
			}
		}
		return new SkillView(id, name, description, body);
	}

	static String write(SkillView skill) {
		return """
				---
				name: %s
				description: %s
				---

				%s
				""".formatted(skill.name(), nullToEmpty(skill.description()), nullToEmpty(skill.body()).trim());
	}

	private static String stripQuotes(String value) {
		if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

}
