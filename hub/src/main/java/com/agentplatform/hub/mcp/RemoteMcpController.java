package com.agentplatform.hub.mcp;

import java.util.List;
import java.util.Map;

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
@RequestMapping("/api/remote-mcps")
public class RemoteMcpController {

	private final RemoteMcpService service;

	public RemoteMcpController(RemoteMcpService service) {
		this.service = service;
	}

	@GetMapping
	public List<RemoteMcpDtos.View> list() {
		return service.list();
	}

	@PostMapping
	public RemoteMcpDtos.View create(@Valid @RequestBody RemoteMcpDtos.UpsertRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public RemoteMcpDtos.View update(@PathVariable String id, @Valid @RequestBody RemoteMcpDtos.UpsertRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		service.delete(id);
	}

	@PostMapping("/{id}/refresh")
	public RemoteMcpDtos.View refresh(
			@PathVariable String id,
			@RequestBody(required = false) RemoteMcpDtos.TryRequest request) {
		return service.refresh(id, request == null ? null : request.secret());
	}

	@PostMapping("/{id}/try")
	public Map<String, String> tryConnect(
			@PathVariable String id,
			@RequestBody(required = false) RemoteMcpDtos.TryRequest request) {
		return Map.of("output", service.tryConnect(id, request == null ? null : request.secret()));
	}

}
