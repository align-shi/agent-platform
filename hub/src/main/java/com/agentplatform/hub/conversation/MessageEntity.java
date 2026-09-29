package com.agentplatform.hub.conversation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "messages")
public class MessageEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "conversation_id", nullable = false, length = 36)
	private String conversationId;

	@Column(nullable = false, length = 16)
	private String role;

	@Column(nullable = false, columnDefinition = "TEXT")
	private String content;

	@Column(columnDefinition = "TEXT")
	private String citations;

	@Column(nullable = false)
	private int seq;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getConversationId() {
		return conversationId;
	}

	public void setConversationId(String conversationId) {
		this.conversationId = conversationId;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public String getCitations() {
		return citations;
	}

	public void setCitations(String citations) {
		this.citations = citations;
	}

	public int getSeq() {
		return seq;
	}

	public void setSeq(int seq) {
		this.seq = seq;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
