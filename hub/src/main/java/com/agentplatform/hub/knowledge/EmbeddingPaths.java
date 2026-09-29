package com.agentplatform.hub.knowledge;

final class EmbeddingPaths {

	private EmbeddingPaths() {
	}

	static String resolve(String baseUrl) {
		if (baseUrl == null || baseUrl.isBlank()) {
			throw new IllegalArgumentException("baseUrl is required");
		}
		String base = trimSlash(baseUrl.trim());
		String lower = base.toLowerCase();
		if (lower.endsWith("/embeddings")) {
			return base;
		}
		if (hasVersionedRoot(lower)) {
			return base + "/embeddings";
		}
		return base + "/v1/embeddings";
	}

	private static boolean hasVersionedRoot(String base) {
		return base.matches(".*/v[0-9]+")
				|| base.contains("/paas/v")
				|| base.contains("/compatible-mode");
	}

	private static String trimSlash(String url) {
		if (url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}

}
