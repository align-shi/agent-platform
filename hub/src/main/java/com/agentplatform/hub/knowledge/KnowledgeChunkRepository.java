package com.agentplatform.hub.knowledge;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunkEntity, String> {

	List<KnowledgeChunkEntity> findByKnowledgeBaseId(String knowledgeBaseId);

	long countByDocumentId(String documentId);

	void deleteByDocumentId(String documentId);

	void deleteByKnowledgeBaseId(String knowledgeBaseId);

}
