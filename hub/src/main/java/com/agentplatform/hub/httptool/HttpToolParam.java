package com.agentplatform.hub.httptool;

public record HttpToolParam(
		String name,
		String in,
		String type,
		boolean required,
		String description) {
}
