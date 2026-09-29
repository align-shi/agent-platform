package com.agentplatform.hub.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

	@Test
	void shortTextStaysOneChunk() {
		assertEquals(1, TextChunker.split("你好", 800, 80).size());
	}

	@Test
	void splitsLongTextAndFinishes() {
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 40; i++) {
			text.append("第").append(i).append("段说明。这里是一段足够长的正文，用来检查分块会在段落附近断开。\n\n");
		}
		var chunks = TextChunker.split(text.toString(), 200, 40);
		assertTrue(chunks.size() > 1);
		assertTrue(chunks.size() < 80);
		for (String chunk : chunks) {
			assertTrue(chunk.length() <= 200);
			assertTrue(!chunk.isBlank());
		}
	}

}
