package com.agentplatform.hub.skill;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentSkillRepository extends JpaRepository<AgentSkillEntity, String> {

	List<AgentSkillEntity> findByAgentIdOrderBySkillIdAsc(String agentId);

	void deleteByAgentId(String agentId);

	void deleteBySkillId(String skillId);

}
