package com.agentplatform.hub.workflow;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

	private final WorkflowService service;

	public WorkflowController(WorkflowService service) {
		this.service = service;
	}

	@GetMapping
	public List<WorkflowDtos.View> list() {
		return service.list();
	}

	@GetMapping("/{id}")
	public WorkflowDtos.View get(@PathVariable String id) {
		return service.get(id);
	}

	@PostMapping
	public WorkflowDtos.View create(@RequestBody(required = false) WorkflowDtos.UpsertRequest request) {
		return service.create(request == null ? new WorkflowDtos.UpsertRequest(null, true, null) : request);
	}

	@PutMapping("/{id}")
	public WorkflowDtos.View update(@PathVariable String id, @RequestBody WorkflowDtos.UpsertRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		service.delete(id);
	}

	@GetMapping("/{id}/runs")
	public List<WorkflowDtos.RunSummary> listRuns(@PathVariable String id) {
		return service.listRuns(id);
	}

	@GetMapping("/{id}/runs/{runId}")
	public WorkflowDtos.RunView getRun(@PathVariable String id, @PathVariable String runId) {
		return service.getRun(id, runId);
	}

	@PostMapping("/{id}/run")
	public WorkflowDtos.RunView run(@PathVariable String id, @RequestBody(required = false) WorkflowDtos.RunRequest request) {
		return service.run(id, request);
	}

}
