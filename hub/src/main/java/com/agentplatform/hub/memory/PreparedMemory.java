package com.agentplatform.hub.memory;

import java.util.List;

import com.agentplatform.hub.conversation.ConversationDtos;

public record PreparedMemory(String system, List<ConversationDtos.MessageView> history) {
}
