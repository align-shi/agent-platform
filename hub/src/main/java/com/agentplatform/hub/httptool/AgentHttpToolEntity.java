package com.agentplatform.hub.httptool;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_http_tools")
public class AgentHttpToolEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "agent_id", nullable = false, length = 36)
	private String agentId;

	@Column(name = "http_tool_id", nullable = false, length = 36)
	private String httpToolId;

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

	public String getHttpToolId() {
		return httpToolId;
	}

	public void setHttpToolId(String httpToolId) {
		this.httpToolId = httpToolId;
	}

}
