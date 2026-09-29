package com.agentplatform.hub.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, String> {

	boolean existsByEmbeddingProviderId(String embeddingProviderId);

}
