package com.agentplatform.hub.feishu;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeishuSeenEventRepository extends JpaRepository<FeishuSeenEventEntity, String> {

	void deleteByBotId(String botId);

	long deleteByCreatedAtBefore(Instant createdAt);

}
