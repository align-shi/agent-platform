package com.agentplatform.hub.agent;

public enum MemoryMode {

	NONE,
	SESSION,
	CROSS;

	public static MemoryMode from(String value) {
		if (value == null || value.isBlank()) {
			return SESSION;
		}
		try {
			return MemoryMode.valueOf(value.trim().toUpperCase());
		}
		catch (IllegalArgumentException ex) {
			return SESSION;
		}
	}

	public boolean keepsSession() {
		return this == SESSION || this == CROSS;
	}

	public boolean keepsCross() {
		return this == CROSS;
	}

}
