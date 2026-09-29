package com.agentplatform.hub.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class McpJsonTest {

	@Test
	void roundTripsNestedToolSchema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		Map<String, Object> query = new LinkedHashMap<>();
		query.put("type", "string");
		query.put("description", "关键词");
		properties.put("q", query);
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		schema.put("properties", properties);
		schema.put("required", List.of("q"));
		Map<String, Object> tool = new LinkedHashMap<>();
		tool.put("name", "search");
		tool.put("description", "查找");
		tool.put("inputSchema", schema);
		String json = McpJson.write(List.of(tool));
		Object parsed = McpJson.read(json);
		assertTrue(parsed instanceof List<?>);
		@SuppressWarnings("unchecked")
		Map<String, Object> first = (Map<String, Object>) ((List<?>) parsed).get(0);
		assertEquals("search", first.get("name"));
		@SuppressWarnings("unchecked")
		Map<String, Object> input = (Map<String, Object>) first.get("inputSchema");
		assertEquals("object", input.get("type"));
		assertEquals(List.of("q"), input.get("required"));
	}

	@Test
	void parsesSseJsonRpcById() {
		String body = """
				event: message
				data: {"jsonrpc":"2.0","id":1,"result":{"ok":true}}

				event: message
				data: {"jsonrpc":"2.0","id":2,"result":{"tools":[]}}
				""";
		Map<String, Object> payload = McpSse.jsonRpc(McpSse.parse(body), 2);
		assertEquals("2.0", payload.get("jsonrpc"));
		assertEquals("2", McpJson.idKey(payload.get("id")));
	}

	@Test
	void readsSseEndpointEvent() {
		String body = """
				event: endpoint
				data: /messages?sessionId=abc
				""";
		assertEquals("/messages?sessionId=abc", McpSse.endpoint(McpSse.parse(body)));
	}

	@Test
	void sanitizesToolNamesAndAvoidsCollision() {
		assertEquals("browser_navigate", RemoteMcpNames.expose("browser.navigate"));
		String unique = RemoteMcpNames.unique("search", Set.of("search"), "abcd1234-xxxx");
		assertTrue(unique.startsWith("mcp_"));
		assertTrue(!unique.equals("search"));
	}

}
