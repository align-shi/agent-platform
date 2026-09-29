package com.agentplatform.hub.knowledge;

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
@RequestMapping("/api/knowledge-bases")
public class KnowledgeController {

	private final KnowledgeService service;

	public KnowledgeController(KnowledgeService service) {
		this.service = service;
	}

	@GetMapping
	public List<KnowledgeDtos.View> list() {
		return service.list();
	}

	@PostMapping
	public KnowledgeDtos.View create(@Valid @RequestBody KnowledgeDtos.UpsertRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public KnowledgeDtos.View update(@PathVariable String id, @Valid @RequestBody KnowledgeDtos.UpsertRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable String id) {
		service.delete(id);
	}

	@GetMapping("/{id}/documents")
	public List<KnowledgeDtos.DocumentView> documents(@PathVariable String id) {
		return service.listDocuments(id);
	}

	@PostMapping("/{id}/documents")
	public KnowledgeDtos.DocumentView addDocument(
			@PathVariable String id,
			@Valid @RequestBody KnowledgeDtos.DocumentRequest request) {
		return service.addDocument(id, request);
	}

	@DeleteMapping("/{id}/documents/{documentId}")
	public void deleteDocument(@PathVariable String id, @PathVariable String documentId) {
		service.deleteDocument(id, documentId);
	}

}
