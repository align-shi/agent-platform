package com.agentplatform.hub.knowledge;

import java.util.ArrayList;
import java.util.List;

final class TextChunker {

	private TextChunker() {
	}

	static List<String> split(String raw, int size, int overlap) {
		String text = raw == null ? "" : raw.replace("\r\n", "\n").replace('\r', '\n').trim();
		if (text.isEmpty()) {
			return List.of();
		}
		int chunkSize = Math.max(1, size);
		int chunkOverlap = Math.max(0, Math.min(overlap, chunkSize - 1));
		if (text.length() <= chunkSize) {
			return List.of(text);
		}
		List<String> chunks = new ArrayList<>();
		int start = 0;
		while (start < text.length()) {
			int end = Math.min(text.length(), start + chunkSize);
			if (end < text.length()) {
				int breakAt = bestBreak(text, start, end);
				if (breakAt > start) {
					end = breakAt;
				}
			}
			String piece = text.substring(start, end).trim();
			if (!piece.isEmpty()) {
				chunks.add(piece);
			}
			if (end >= text.length()) {
				break;
			}
			int next = end - chunkOverlap;
			start = next <= start ? end : next;
		}
		return chunks;
	}

	private static int bestBreak(String text, int start, int end) {
		int min = start + Math.max(1, (end - start) / 2);
		int paragraph = text.lastIndexOf("\n\n", end - 1);
		if (paragraph >= min) {
			return paragraph;
		}
		int line = text.lastIndexOf('\n', end - 1);
		if (line >= min) {
			return line;
		}
		int space = text.lastIndexOf(' ', end - 1);
		if (space >= min) {
			return space;
		}
		return end;
	}

}
