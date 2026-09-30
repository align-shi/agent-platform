package com.agentplatform.hub.workflow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WorkflowGraphTest {

	@Test
	void acceptsBlankStartToEnd() {
		assertDoesNotThrow(() -> WorkflowGraph.validate(WorkflowGraph.blank()));
	}

	@Test
	void rejectsMissingConditionBranches() {
		WorkflowDtos.Graph graph = new WorkflowDtos.Graph(
				List.of(
						new WorkflowDtos.Node("start", "start", 0, 0, Map.of()),
						new WorkflowDtos.Node("cond", "condition", 1, 0, Map.of()),
						new WorkflowDtos.Node("end", "end", 2, 0, Map.of())),
				List.of(new WorkflowDtos.Edge("a", "start", "cond", null)));
		assertThrows(ResponseStatusException.class, () -> WorkflowGraph.validate(graph));
	}

	@Test
	void conditionContainsAndEquals() {
		assertTrue(WorkflowGraph.matches("订单已发货", Map.of("operator", "contains", "value", "发货")));
		assertFalse(WorkflowGraph.matches("订单已发货", Map.of("operator", "equals", "value", "发货")));
		assertTrue(WorkflowGraph.matches("hello", Map.of("operator", "not_empty")));
		assertFalse(WorkflowGraph.matches("  ", Map.of("operator", "not_empty")));
	}

	@Test
	void rendersInputAndOutput() {
		assertEquals("问：你好 / 答：在", WorkflowGraph.render("问：{{input}} / 答：{{output}}", "你好", "在"));
	}

	@Test
	void acceptsLlmBetweenStartAndEnd() {
		WorkflowDtos.Graph graph = new WorkflowDtos.Graph(
				List.of(
						new WorkflowDtos.Node("start", "start", 0, 0, Map.of()),
						new WorkflowDtos.Node("ask", "llm", 1, 0, Map.of()),
						new WorkflowDtos.Node("end", "end", 2, 0, Map.of())),
				List.of(
						new WorkflowDtos.Edge("a", "start", "ask", null),
						new WorkflowDtos.Edge("b", "ask", "end", null)));
		assertDoesNotThrow(() -> WorkflowGraph.validate(graph));
	}

}
