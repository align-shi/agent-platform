package com.agentplatform.hub.agent;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

	private final AgentService service;

	public AgentController(AgentService service) {
		this.service = service;
	}

	@GetMapping
	public List<AgentDtos.View> list() {
		return service.list();
	}

	@PostMapping
	public AgentDtos.View create(@Valid @RequestBody AgentDtos.UpsertRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public AgentDtos.View update(@PathVariable String id, @Valid @RequestBody AgentDtos.UpsertRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		service.delete(id);
	}

	@DeleteMapping("/{id}/memory-facts")
	public AgentDtos.View clearMemoryFacts(@PathVariable String id) {
		return service.clearFacts(id);
	}

}
