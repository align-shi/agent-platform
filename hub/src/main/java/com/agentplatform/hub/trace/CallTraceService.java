package com.agentplatform.hub.trace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CallTraceService {

	private static final Logger log = LoggerFactory.getLogger(CallTraceService.class);
	private static final int PAGE_SIZE = 100;

	private final AgentCallRepository repository;

	public CallTraceService(AgentCallRepository repository) {
		this.repository = repository;
	}

	public void save(CallTraceRecorder.Draft draft) {
		try {
			repository.save(toEntity(draft));
		}
		catch (RuntimeException ex) {
			log.warn("failed to save call trace {}", draft.id(), ex);
		}
	}

	@Transactional(readOnly = true)
	public List<CallDtos.Summary> list(String agentId) {
		String filter = agentId == null ? "" : agentId;
		List<CallDtos.Summary> rows = new ArrayList<>();
		for (Object[] row : repository.listRows(filter, PageRequest.of(0, PAGE_SIZE))) {
			rows.add(toSummary(row));
		}
		return rows;
	}

	@Transactional(readOnly = true)
	public CallDtos.Detail get(String id) {
		AgentCallEntity entity = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Call not found"));
		return new CallDtos.Detail(toSummary(entity), CallTraceJson.decode(entity.getTraceJson()));
	}

	@Transactional
	public void deleteByConversationId(String conversationId) {
		repository.deleteByConversationId(conversationId);
	}

	@Transactional
	public void deleteByAgentId(String agentId) {
		repository.deleteByAgentId(agentId);
	}

	private static AgentCallEntity toEntity(CallTraceRecorder.Draft draft) {
		AgentCallEntity entity = new AgentCallEntity();
		entity.setId(draft.id());
		entity.setAgentId(draft.agentId());
		entity.setAgentName(draft.agentName());
		entity.setConversationId(draft.conversationId());
		entity.setProviderId(draft.providerId());
		entity.setModel(draft.model());
		entity.setStatus(draft.status());
		entity.setUserInput(clip(draft.userInput(), 4000));
		entity.setStartedAt(draft.startedAt());
		entity.setLatencyMs(draft.latencyMs());
		entity.setRounds(draft.rounds());
		entity.setModelCalls(draft.modelCalls());
		entity.setToolCalls(draft.toolCalls());
		entity.setMcpCalls(draft.mcpCalls());
		entity.setPromptTokens(draft.promptTokens());
		entity.setCompletionTokens(draft.completionTokens());
		entity.setTotalTokens(draft.totalTokens());
		entity.setError(blankToNull(clip(draft.error(), 2000)));
		entity.setTraceJson(draft.traceJson() == null || draft.traceJson().isBlank() ? "{\"spans\":[]}" : draft.traceJson());
		return entity;
	}

	private static CallDtos.Summary toSummary(AgentCallEntity entity) {
		return new CallDtos.Summary(
				entity.getId(),
				entity.getAgentId(),
				entity.getAgentName(),
				entity.getConversationId(),
				entity.getModel(),
				entity.getStatus(),
				entity.getUserInput() == null ? "" : entity.getUserInput(),
				entity.getStartedAt(),
				entity.getLatencyMs(),
				entity.getRounds(),
				entity.getModelCalls(),
				entity.getToolCalls(),
				entity.getMcpCalls(),
				entity.getPromptTokens(),
				entity.getCompletionTokens(),
				entity.getTotalTokens(),
				entity.getError());
	}

	private static CallDtos.Summary toSummary(Object[] row) {
		int index = 0;
		return new CallDtos.Summary(
				text(row[index++]),
				text(row[index++]),
				text(row[index++]),
				text(row[index++]),
				text(row[index++]),
				text(row[index++]),
				text(row[index++]),
				toInstant(row[index++]),
				toLong(row[index++]),
				toInt(row[index++]),
				toInt(row[index++]),
				toInt(row[index++]),
				toInt(row[index++]),
				toInt(row[index++]),
				toInt(row[index++]),
				toInt(row[index++]),
				blankToNull(text(row[index])));
	}

	private static Instant toInstant(Object value) {
		if (value instanceof Instant instant) {
			return instant;
		}
		if (value instanceof java.util.Date date) {
			return date.toInstant();
		}
		if (value instanceof java.time.OffsetDateTime offset) {
			return offset.toInstant();
		}
		if (value instanceof java.time.LocalDateTime local) {
			return local.atZone(java.time.ZoneId.systemDefault()).toInstant();
		}
		return Instant.EPOCH;
	}

	private static long toLong(Object value) {
		return value instanceof Number number ? number.longValue() : 0L;
	}

	private static int toInt(Object value) {
		return value instanceof Number number ? number.intValue() : 0;
	}

	private static String text(Object value) {
		return value == null ? "" : String.valueOf(value);
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String clip(String text, int max) {
		if (text == null) {
			return "";
		}
		if (text.length() <= max) {
			return text;
		}
		return text.substring(0, max);
	}

}
