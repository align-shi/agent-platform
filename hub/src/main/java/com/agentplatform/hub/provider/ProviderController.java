package com.agentplatform.hub.provider;

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
@RequestMapping("/api/providers")
public class ProviderController {

	private final ProviderService service;

	public ProviderController(ProviderService service) {
		this.service = service;
	}

	@GetMapping
	public List<ProviderDtos.View> list() {
		return service.list();
	}

	@PostMapping
	public ProviderDtos.View create(@Valid @RequestBody ProviderDtos.UpsertRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public ProviderDtos.View update(
			@PathVariable String id,
			@Valid @RequestBody ProviderDtos.UpsertRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		service.delete(id);
	}

}
