package com.agentplatform.hub.httptool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HttpToolRepository extends JpaRepository<HttpToolEntity, String> {

	boolean existsByToolNameIgnoreCase(String toolName);

	boolean existsByToolNameIgnoreCaseAndIdNot(String toolName, String id);

	List<HttpToolEntity> findByConnectorIdOrderBySortOrderAsc(String connectorId);

	void deleteByConnectorId(String connectorId);

}
