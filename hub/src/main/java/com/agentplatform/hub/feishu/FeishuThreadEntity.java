package com.agentplatform.hub.feishu;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
		name = "feishu_threads",
		uniqueConstraints = @UniqueConstraint(name = "uk_feishu_thread_chat", columnNames = {"bot_id", "chat_id"}))
public class FeishuThreadEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "bot_id", nullable = false, length = 36)
	private String botId;

	@Column(name = "chat_id", nullable = false, length = 128)
	private String chatId;

	@Column(name = "conversation_id", nullable = false, length = 36)
	private String conversationId;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getBotId() {
		return botId;
	}

	public void setBotId(String botId) {
		this.botId = botId;
	}

	public String getChatId() {
		return chatId;
	}

	public void setChatId(String chatId) {
		this.chatId = chatId;
	}

	public String getConversationId() {
		return conversationId;
	}

	public void setConversationId(String conversationId) {
		this.conversationId = conversationId;
	}

}
