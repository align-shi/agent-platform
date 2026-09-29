package com.agentplatform.hub.feishu;

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

@RestController
@RequestMapping("/api/feishu/bots")
public class FeishuBotController {

	private final FeishuBotService service;

	public FeishuBotController(FeishuBotService service) {
		this.service = service;
	}

	@GetMapping
	public List<FeishuDtos.View> list() {
		return service.list();
	}

	@PostMapping
	public FeishuDtos.View create(@RequestBody FeishuDtos.UpsertRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public FeishuDtos.View update(@PathVariable String id, @RequestBody FeishuDtos.UpsertRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		service.delete(id);
	}

	@PostMapping("/{id}/probe")
	public Map<String, Boolean> probe(@PathVariable String id) {
		service.probe(id);
		return Map.of("ok", true);
	}

}
