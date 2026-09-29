package com.agentplatform.hub.mcp;

import java.util.Locale;

final class RemoteMcpNames {

	private RemoteMcpNames() {
	}

	static String expose(String raw) {
		if (raw == null || raw.isBlank()) {
			return "mcp_tool";
		}
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < raw.trim().length(); i++) {
			char ch = raw.trim().charAt(i);
			if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9') || ch == '_' || ch == '-') {
				text.append(ch);
			}
			else {
				text.append('_');
			}
		}
		String name = text.toString();
		while (name.contains("__")) {
			name = name.replace("__", "_");
		}
		if (name.startsWith("_")) {
			name = name.substring(1);
		}
		if (name.isBlank()) {
			name = "mcp_tool";
		}
		if (!Character.isLetter(name.charAt(0))) {
			name = "m_" + name;
		}
		if (name.length() > 64) {
			name = name.substring(0, 64);
		}
		return name;
	}

	static String unique(String preferred, java.util.Set<String> taken, String serverId) {
		String base = expose(preferred);
		if (!taken.contains(base.toLowerCase(Locale.ROOT))) {
			return base;
		}
		String prefixed = expose("mcp_" + compact(serverId) + "_" + preferred);
		if (!taken.contains(prefixed.toLowerCase(Locale.ROOT))) {
			return prefixed;
		}
		for (int i = 2; i < 100; i++) {
			String candidate = trim64(prefixed + "_" + i);
			if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
				return candidate;
			}
		}
		return trim64(prefixed + "_" + Math.abs(preferred.hashCode()));
	}

	private static String compact(String serverId) {
		if (serverId == null) {
			return "s";
		}
		String compact = serverId.replace("-", "");
		return compact.length() <= 8 ? compact : compact.substring(0, 8);
	}

	private static String trim64(String name) {
		return name.length() <= 64 ? name : name.substring(0, 64);
	}

}
