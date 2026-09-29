package com.agentplatform.hub.mcp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_mcp_servers")
public class AgentMcpEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "agent_id", nullable = false, length = 36)
	private String agentId;

	@Column(name = "mcp_server_id", nullable = false, length = 36)
	private String mcpServerId;

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

	public String getMcpServerId() {
		return mcpServerId;
	}

	public void setMcpServerId(String mcpServerId) {
		this.mcpServerId = mcpServerId;
	}

}
