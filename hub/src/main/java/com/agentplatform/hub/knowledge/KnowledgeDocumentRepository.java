package com.agentplatform.hub.knowledge;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, String> {

	List<KnowledgeDocumentEntity> findByKnowledgeBaseIdOrderByCreatedAtAsc(String knowledgeBaseId);

	long countByKnowledgeBaseId(String knowledgeBaseId);

	void deleteByKnowledgeBaseId(String knowledgeBaseId);

}
