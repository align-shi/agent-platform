package com.agentplatform.hub.httptool;

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
@RequestMapping("/api/http-tools")
public class HttpToolController {

	private final HttpToolService service;

	public HttpToolController(HttpToolService service) {
		this.service = service;
	}

	@GetMapping
	public List<HttpToolDtos.View> list() {
		return service.list();
	}

	@PostMapping
	public HttpToolDtos.View create(@Valid @RequestBody HttpToolDtos.UpsertRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public HttpToolDtos.View update(@PathVariable String id, @Valid @RequestBody HttpToolDtos.UpsertRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		service.delete(id);
	}

	@PostMapping("/{id}/tools/{toolId}/try")
	public Map<String, String> tryCall(
			@PathVariable String id,
			@PathVariable String toolId,
			@RequestBody(required = false) HttpToolDtos.TryRequest request) {
		HttpToolEntity tool = service.requireTool(id, toolId);
		Map<String, String> arguments = request == null || request.arguments() == null ? Map.of() : request.arguments();
		String json = JsonLite.objectToJson(arguments, tool.parameters());
		String secret = request == null ? null : request.secret();
		return Map.of("output", service.invoke(tool, json, secret));
	}

}
