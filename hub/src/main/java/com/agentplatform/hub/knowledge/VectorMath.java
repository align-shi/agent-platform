package com.agentplatform.hub.knowledge;

final class VectorMath {

	private VectorMath() {
	}

	static double cosine(double[] left, double[] right) {
		if (left == null || right == null || left.length == 0 || left.length != right.length) {
			return 0;
		}
		double dot = 0;
		double leftNorm = 0;
		double rightNorm = 0;
		for (int i = 0; i < left.length; i++) {
			dot += left[i] * right[i];
			leftNorm += left[i] * left[i];
			rightNorm += right[i] * right[i];
		}
		if (leftNorm == 0 || rightNorm == 0) {
			return 0;
		}
		return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
	}

}
