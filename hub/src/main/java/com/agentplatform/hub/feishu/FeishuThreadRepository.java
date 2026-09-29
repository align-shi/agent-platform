package com.agentplatform.hub.feishu;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FeishuThreadRepository extends JpaRepository<FeishuThreadEntity, String> {

	Optional<FeishuThreadEntity> findByBotIdAndChatId(String botId, String chatId);

	void deleteByBotId(String botId);

}
