package com.agentplatform.hub.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "agent_skills")
public class AgentSkillEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "agent_id", nullable = false, length = 36)
	private String agentId;

	@Column(name = "skill_id", nullable = false, length = 64)
	private String skillId;

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

	public String getSkillId() {
		return skillId;
	}

	public void setSkillId(String skillId) {
		this.skillId = skillId;
	}

}
