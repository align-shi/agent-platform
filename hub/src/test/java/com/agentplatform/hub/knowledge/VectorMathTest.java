package com.agentplatform.hub.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VectorMathTest {

	@Test
	void identicalVectorsScoreOne() {
		assertEquals(1, VectorMath.cosine(new double[] { 1, 0 }, new double[] { 1, 0 }), 0.0001);
	}

	@Test
	void orthogonalVectorsScoreZero() {
		assertEquals(0, VectorMath.cosine(new double[] { 1, 0 }, new double[] { 0, 1 }), 0.0001);
	}

	@Test
	void roundTripJson() {
		double[] vector = EmbeddingVectors.decode(EmbeddingVectors.encode(new double[] { 0.25, -1.5, 3 }));
		assertEquals(3, vector.length);
		assertEquals(0.25, vector[0], 0.0001);
		assertEquals(-1.5, vector[1], 0.0001);
	}

}
