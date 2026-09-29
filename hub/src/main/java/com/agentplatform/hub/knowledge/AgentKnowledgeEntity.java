package com.agentplatform.hub.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_knowledge_bases")
public class AgentKnowledgeEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "agent_id", nullable = false, length = 36)
	private String agentId;

	@Column(name = "knowledge_base_id", nullable = false, length = 36)
	private String knowledgeBaseId;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getAgentId() {
		return agentId;
	}

	public void setAgentId(String agentId) {
		this.agentId = agentId;
	}

	public String getKnowledgeBaseId() {
		return knowledgeBaseId;
	}

	public void setKnowledgeBaseId(String knowledgeBaseId) {
		this.knowledgeBaseId = knowledgeBaseId;
	}

}
