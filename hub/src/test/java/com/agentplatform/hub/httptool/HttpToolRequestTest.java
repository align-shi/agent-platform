package com.agentplatform.hub.httptool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HttpToolRequestTest {

	@Test
	void buildsQueryAndPath() {
		HttpToolEntity tool = new HttpToolEntity();
		tool.setMethod("GET");
		tool.setUrl("https://api.example.com/orders/{orderId}");
		tool.setParametersJson(JsonLite.paramsToJson(List.of(
				new HttpToolParam("orderId", "path", "string", true, "订单号"),
				new HttpToolParam("lang", "query", "string", false, "语言"))));
		HttpToolRequest request = HttpToolRequest.build(tool, Map.of("orderId", "A1", "lang", "zh"));
		assertEquals("GET", request.method());
		assertEquals("https://api.example.com/orders/A1?lang=zh", request.uri().toString());
	}

	@Test
	void postsJsonBody() {
		HttpToolEntity tool = new HttpToolEntity();
		tool.setMethod("POST");
		tool.setUrl("https://api.example.com/search");
		tool.setParametersJson(JsonLite.paramsToJson(List.of(
				new HttpToolParam("keyword", "body", "string", true, "关键词"))));
		HttpToolRequest request = HttpToolRequest.build(tool, Map.of("keyword", "发票"));
		assertEquals("{\"keyword\":\"发票\"}", request.body());
		assertEquals("application/json", request.headers().get("Content-Type"));
	}

	@Test
	void rejectsMissingRequired() {
		HttpToolEntity tool = new HttpToolEntity();
		tool.setMethod("GET");
		tool.setUrl("https://api.example.com/x");
		tool.setParametersJson(JsonLite.paramsToJson(List.of(
				new HttpToolParam("id", "query", "string", true, ""))));
		assertThrows(IllegalArgumentException.class, () -> HttpToolRequest.build(tool, Map.of()));
	}

	@Test
	void rejectsReservedToolNames() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> HttpToolRequest.validateToolName("load_skill"));
		assertTrue(ex.getMessage().contains("冲突"));
	}

	@Test
	void paramsRoundTrip() {
		List<HttpToolParam> params = List.of(new HttpToolParam("q", "query", "string", true, "搜索"));
		assertEquals("q", JsonLite.paramsFromJson(JsonLite.paramsToJson(params)).get(0).name());
	}

}
