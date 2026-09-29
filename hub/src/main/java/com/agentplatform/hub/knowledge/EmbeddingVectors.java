package com.agentplatform.hub.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.json.JsonParserFactory;

final class EmbeddingVectors {

	private EmbeddingVectors() {
	}

	static String encode(double[] vector) {
		StringBuilder json = new StringBuilder("[");
		for (int i = 0; i < vector.length; i++) {
			if (i > 0) {
				json.append(',');
			}
			json.append(vector[i]);
		}
		return json.append(']').toString();
	}

	static double[] decode(String json) {
		if (json == null || json.isBlank()) {
			return new double[0];
		}
		List<Object> values = JsonParserFactory.getJsonParser().parseList(json);
		double[] vector = new double[values.size()];
		for (int i = 0; i < values.size(); i++) {
			vector[i] = number(values.get(i));
		}
		return vector;
	}

	static List<double[]> parseResponse(String json) {
		Map<String, Object> root = JsonParserFactory.getJsonParser().parseMap(json);
		Object data = root.get("data");
		if (!(data instanceof List<?> rows) || rows.isEmpty()) {
			throw new IllegalStateException("Embedding 接口没有返回向量");
		}
		double[][] ordered = new double[rows.size()][];
		int fallback = 0;
		for (Object row : rows) {
			if (!(row instanceof Map<?, ?> map)) {
				throw new IllegalStateException("Embedding 接口返回格式无法识别");
			}
			double[] vector = toVector(map.get("embedding"));
			int index = fallback;
			if (map.get("index") instanceof Number number) {
				index = number.intValue();
			}
			if (index < 0 || index >= ordered.length || ordered[index] != null) {
				index = fallback;
			}
			ordered[index] = vector;
			fallback++;
		}
		List<double[]> vectors = new ArrayList<>();
		for (double[] vector : ordered) {
			if (vector == null || vector.length == 0) {
				throw new IllegalStateException("Embedding 接口返回的向量不完整");
			}
			vectors.add(vector);
		}
		return vectors;
	}

	private static double[] toVector(Object value) {
		if (!(value instanceof List<?> rows) || rows.isEmpty()) {
			throw new IllegalStateException("Embedding 接口没有返回向量");
		}
		double[] vector = new double[rows.size()];
		for (int i = 0; i < rows.size(); i++) {
			vector[i] = number(rows.get(i));
		}
		return vector;
	}

	private static double number(Object value) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		throw new IllegalStateException("Embedding 接口返回的向量无法解析");
	}

}
