package com.agentplatform.hub.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmbeddingPathsTest {

	@Test
	void dashscopeCompatibleMode() {
		assertEquals(
				"https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings",
				EmbeddingPaths.resolve("https://dashscope.aliyuncs.com/compatible-mode/v1"));
	}

	@Test
	void zhipuUsesV4() {
		assertEquals(
				"https://open.bigmodel.cn/api/paas/v4/embeddings",
				EmbeddingPaths.resolve("https://open.bigmodel.cn/api/paas/v4/"));
	}

	@Test
	void hostWithoutVersionGetsV1() {
		assertEquals(
				"https://api.openai.com/v1/embeddings",
				EmbeddingPaths.resolve("https://api.openai.com"));
	}

	@Test
	void alreadyEmbeddings() {
		assertEquals(
				"https://example.com/v1/embeddings",
				EmbeddingPaths.resolve("https://example.com/v1/embeddings"));
	}

}
