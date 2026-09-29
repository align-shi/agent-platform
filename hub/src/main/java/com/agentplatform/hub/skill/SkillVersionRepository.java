package com.agentplatform.hub.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillVersionRepository extends JpaRepository<SkillVersionEntity, String> {

	List<SkillVersionEntity> findBySkillIdOrderByVersionDesc(String skillId);

	Optional<SkillVersionEntity> findBySkillIdAndVersion(String skillId, int version);

	void deleteBySkillId(String skillId);

}
