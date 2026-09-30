package com.agentplatform.hub.workflow;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class WorkflowDtos {

	private WorkflowDtos() {
	}

	public record Node(String id, String type, double x, double y, Map<String, Object> data) {
	}

	public record Edge(String id, String source, String target, String branch) {
	}

	public record Graph(List<Node> nodes, List<Edge> edges) {
	}

	public record UpsertRequest(String name, boolean enabled, Graph graph) {
	}

	public record View(
			String id,
			String name,
			boolean enabled,
			Graph graph,
			String lastStatus,
			String lastError,
			Instant lastRunAt,
			Instant updatedAt) {
	}

	public record RunRequest(String input) {
	}

	public record StepView(String nodeId, String nodeType, String status, String input, String output, String error) {
	}

	public record RunView(
			String id,
			String workflowId,
			String status,
			String input,
			String output,
			String error,
			List<StepView> steps,
			Instant createdAt,
			Instant finishedAt) {
	}

	public record RunSummary(
			String id,
			String status,
			String input,
			String output,
			String error,
			Instant createdAt,
			Instant finishedAt) {
	}

}
