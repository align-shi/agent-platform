package com.agentplatform.hub.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EmbeddingVectorsTest {

	@Test
	void ordersByIndex() {
		String json = """
				{"data":[
				  {"index":1,"embedding":[0.2,0.3]},
				  {"index":0,"embedding":[0.8,0.1]}
				]}
				""";
		var vectors = EmbeddingVectors.parseResponse(json);
		assertEquals(2, vectors.size());
		assertEquals(0.8, vectors.get(0)[0], 0.0001);
		assertEquals(0.2, vectors.get(1)[0], 0.0001);
	}

}
