package com.agentplatform.hub.feishu;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "feishu_seen_events", indexes = @Index(name = "idx_feishu_seen_created", columnList = "created_at"))
public class FeishuSeenEventEntity {

	@Id
	@Column(name = "event_id", length = 128)
	private String eventId;

	@Column(name = "bot_id", nullable = false, length = 36)
	private String botId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getBotId() {
		return botId;
	}

	public void setBotId(String botId) {
		this.botId = botId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
