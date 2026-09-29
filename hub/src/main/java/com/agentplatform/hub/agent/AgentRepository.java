package com.agentplatform.hub.agent;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<AgentEntity, String> {

	Optional<AgentEntity> findByCode(String code);

}
