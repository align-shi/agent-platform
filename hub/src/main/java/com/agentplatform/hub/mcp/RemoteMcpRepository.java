package com.agentplatform.hub.mcp;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RemoteMcpRepository extends JpaRepository<RemoteMcpEntity, String> {

	List<RemoteMcpEntity> findAllByOrderByNameAsc();

}
