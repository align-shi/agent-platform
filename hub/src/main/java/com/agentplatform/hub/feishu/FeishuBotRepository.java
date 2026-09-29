package com.agentplatform.hub.feishu;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeishuBotRepository extends JpaRepository<FeishuBotEntity, String> {

	Optional<FeishuBotEntity> findByAppId(String appId);

}
