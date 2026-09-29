package com.agentplatform.hub.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SkillFileRepository extends JpaRepository<SkillFileEntity, String> {

	List<SkillFileEntity> findBySkillIdAndVersionOrderByPathAsc(String skillId, int version);

	Optional<SkillFileEntity> findBySkillIdAndVersionAndPath(String skillId, int version, String path);

	boolean existsBySkillIdAndVersionAndPath(String skillId, int version, String path);

	void deleteBySkillId(String skillId);

	void deleteBySkillIdAndVersion(String skillId, int version);

	@Query("select f from SkillFileEntity f where f.version = 0 and f.path = 'SKILL.md'")
	List<SkillFileEntity> findWorkingCopies();

}
