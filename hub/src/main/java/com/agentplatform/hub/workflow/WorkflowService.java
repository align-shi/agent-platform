package com.agentplatform.hub.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WorkflowService {

	private static final int MAX_NESTING = 2;
	private static final ThreadLocal<Integer> NESTING = ThreadLocal.withInitial(() -> 0);

	private final WorkflowRepository workflows;
	private final WorkflowRunRepository runs;
	private final WorkflowNodeRunRepository nodeRuns;
	private final AgentWorkflowRepository bindings;
	private final WorkflowEngine engine;
	private final JsonMapper mapper;
	private final TransactionTemplate transactions;

	public WorkflowService(
			WorkflowRepository workflows,
			WorkflowRunRepository runs,
			WorkflowNodeRunRepository nodeRuns,
			AgentWorkflowRepository bindings,
			WorkflowEngine engine,
			JsonMapper mapper,
			PlatformTransactionManager transactionManager) {
		this.workflows = workflows;
		this.runs = runs;
		this.nodeRuns = nodeRuns;
		this.bindings = bindings;
		this.engine = engine;
		this.mapper = mapper;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Transactional(readOnly = true)
	public List<WorkflowDtos.View> list() {
		return workflows.findAll().stream()
				.sorted(Comparator.comparing(WorkflowEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
				.map(this::toView)
				.toList();
	}

	@Transactional(readOnly = true)
	public WorkflowDtos.View get(String id) {
		return toView(require(id));
	}

	@Transactional
	public WorkflowDtos.View create(WorkflowDtos.UpsertRequest request) {
		WorkflowEntity entity = new WorkflowEntity();
		entity.setId(UUID.randomUUID().toString());
		apply(entity, request, true);
		return toView(workflows.save(entity));
	}

	@Transactional
	public WorkflowDtos.View update(String id, WorkflowDtos.UpsertRequest request) {
		WorkflowEntity entity = require(id);
		apply(entity, request, false);
		return toView(workflows.save(entity));
	}

	@Transactional
	public void delete(String id) {
		if (!workflows.existsById(id)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "工作流不存在");
		}
		bindings.deleteByWorkflowId(id);
		nodeRuns.deleteByWorkflowId(id);
		runs.deleteByWorkflowId(id);
		workflows.deleteById(id);
	}

	@Transactional(readOnly = true)
	public List<WorkflowDtos.RunSummary> listRuns(String id) {
		require(id);
		return runs.findByWorkflowIdOrderByCreatedAtDesc(id).stream()
				.limit(30)
				.map(this::toSummary)
				.toList();
	}

	@Transactional(readOnly = true)
	public WorkflowDtos.RunView getRun(String workflowId, String runId) {
		require(workflowId);
		WorkflowRunEntity run = runs.findById(runId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "运行记录不存在"));
		if (!workflowId.equals(run.getWorkflowId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "运行记录不存在");
		}
		return toRunView(run, nodeRuns.findByRunIdOrderBySeqAsc(runId).stream().map(this::toStep).toList());
	}

	@Transactional(readOnly = true)
	public List<String> idsForAgent(String agentId) {
		return bindings.findByAgentIdOrderByWorkflowIdAsc(agentId).stream()
				.map(AgentWorkflowEntity::getWorkflowId)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<WorkflowEntity> boundEnabled(String agentId) {
		List<WorkflowEntity> result = new ArrayList<>();
		for (String workflowId : idsForAgent(agentId)) {
			workflows.findById(workflowId).filter(WorkflowEntity::isEnabled).ifPresent(result::add);
		}
		return result;
	}

	@Transactional
	public void replaceBindings(String agentId, List<String> workflowIds) {
		bindings.deleteByAgentId(agentId);
		if (workflowIds == null) {
			return;
		}
		List<AgentWorkflowEntity> rows = new ArrayList<>();
		for (String workflowId : workflowIds) {
			if (workflowId == null || workflowId.isBlank()) {
				continue;
			}
			String normalized = workflowId.trim();
			if (rows.stream().anyMatch((row) -> row.getWorkflowId().equals(normalized))) {
				continue;
			}
			require(normalized);
			AgentWorkflowEntity row = new AgentWorkflowEntity();
			row.setId(UUID.randomUUID().toString());
			row.setAgentId(agentId);
			row.setWorkflowId(normalized);
			rows.add(row);
		}
		bindings.saveAll(rows);
	}

	@Transactional
	public void deleteBindingsForAgent(String agentId) {
		bindings.deleteByAgentId(agentId);
	}

	public String catalogPrompt(List<WorkflowEntity> bound) {
		if (bound == null || bound.isEmpty()) {
			return "";
		}
		StringBuilder prompt = new StringBuilder("可用工作流（固定步骤，用 run_workflow 执行；workflow_id 用下面的 id）：");
		for (WorkflowEntity workflow : bound) {
			prompt.append("\n- ").append(workflow.getId()).append(' ').append(workflow.getName());
		}
		return prompt.toString();
	}

	public List<Map<String, Object>> definitions() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("workflow_id", Map.of("type", "string", "description", "已绑定工作流的 id"));
		properties.put("input", Map.of(
				"type",
				"string",
				"description",
				"交给工作流开始节点的文字。缺省用用户当前这句话"));
		Map<String, Object> parameters = new LinkedHashMap<>();
		parameters.put("type", "object");
		parameters.put("properties", properties);
		parameters.put("required", List.of("workflow_id"));
		Map<String, Object> function = new LinkedHashMap<>();
		function.put("name", "run_workflow");
		function.put("description", "按已绑定工作流的固定步骤执行，返回最终输出。");
		function.put("parameters", parameters);
		return List.of(Map.of("type", "function", "function", function));
	}

	public String invokeAsTool(String agentId, String workflowId, String input) {
		try {
			WorkflowDtos.RunView view = invokeForAgent(agentId, workflowId, input);
			if ("succeeded".equals(view.status())) {
				return view.output() == null || view.output().isBlank() ? "(工作流没有文字输出)" : view.output();
			}
			return "ERROR: " + (view.error() == null || view.error().isBlank() ? "工作流失败" : view.error());
		}
		catch (ResponseStatusException ex) {
			return "ERROR: " + (ex.getReason() == null ? ex.getMessage() : ex.getReason());
		}
		catch (RuntimeException ex) {
			return "ERROR: " + (ex.getMessage() == null ? "工作流失败" : ex.getMessage());
		}
	}

	public WorkflowDtos.RunView invokeForAgent(String agentId, String workflowId, String input) {
		if (workflowId == null || workflowId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请传入 workflow_id");
		}
		String normalized = workflowId.trim();
		if (!idsForAgent(agentId).contains(normalized)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这个工作流没有绑定到当前智能体");
		}
		return run(normalized, new WorkflowDtos.RunRequest(input));
	}

	public WorkflowDtos.RunView run(String id, WorkflowDtos.RunRequest request) {
		int depth = NESTING.get();
		if (depth >= MAX_NESTING) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工作流不能再嵌套调用");
		}
		NESTING.set(depth + 1);
		try {
			WorkflowEntity entity = require(id);
			if (!entity.isEnabled()) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "这个工作流已停用");
			}
			WorkflowDtos.Graph graph = parse(entity.getGraphJson());
			WorkflowGraph.validate(graph);
			String input = request == null || request.input() == null ? "" : request.input();
			WorkflowEngine.Result result = engine.execute(graph, input);
			WorkflowDtos.RunView saved = transactions.execute((status) -> persistRun(entity.getId(), input, result));
			if (saved == null) {
				throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "没有保存运行结果");
			}
			return saved;
		}
		finally {
			if (depth == 0) {
				NESTING.remove();
			}
			else {
				NESTING.set(depth);
			}
		}
	}

	private WorkflowDtos.RunView persistRun(String workflowId, String input, WorkflowEngine.Result result) {
		WorkflowEntity entity = require(workflowId);
		WorkflowRunEntity run = new WorkflowRunEntity();
		run.setId(UUID.randomUUID().toString());
		run.setWorkflowId(workflowId);
		run.setStatus(result.status());
		run.setInputText(input);
		run.setOutputText(result.output());
		run.setError(abbreviate(result.error()));
		run.setCreatedAt(Instant.now());
		run.setFinishedAt(Instant.now());
		runs.save(run);
		int seq = 0;
		List<WorkflowDtos.StepView> steps = result.steps() == null ? List.of() : result.steps();
		for (WorkflowDtos.StepView step : steps) {
			WorkflowNodeRunEntity node = new WorkflowNodeRunEntity();
			node.setId(UUID.randomUUID().toString());
			node.setRunId(run.getId());
			node.setWorkflowId(workflowId);
			node.setSeq(seq++);
			node.setNodeId(step.nodeId());
			node.setNodeType(step.nodeType());
			node.setStatus(step.status());
			node.setInputText(step.input());
			node.setOutputText(step.output());
			node.setError(step.error());
			nodeRuns.save(node);
		}
		entity.setLastStatus(result.status());
		entity.setLastError(abbreviate(result.error()));
		entity.setLastRunAt(Instant.now());
		workflows.save(entity);
		return toRunView(run, steps);
	}

	private WorkflowDtos.RunView toRunView(WorkflowRunEntity run, List<WorkflowDtos.StepView> steps) {
		return new WorkflowDtos.RunView(
				run.getId(),
				run.getWorkflowId(),
				run.getStatus(),
				run.getInputText(),
				run.getOutputText(),
				run.getError(),
				steps,
				run.getCreatedAt(),
				run.getFinishedAt());
	}

	private WorkflowDtos.RunSummary toSummary(WorkflowRunEntity run) {
		return new WorkflowDtos.RunSummary(
				run.getId(),
				run.getStatus(),
				run.getInputText(),
				run.getOutputText(),
				run.getError(),
				run.getCreatedAt(),
				run.getFinishedAt());
	}

	private WorkflowDtos.StepView toStep(WorkflowNodeRunEntity node) {
		return new WorkflowDtos.StepView(
				node.getNodeId(),
				node.getNodeType(),
				node.getStatus(),
				node.getInputText(),
				node.getOutputText(),
				node.getError());
	}

	private void apply(WorkflowEntity entity, WorkflowDtos.UpsertRequest request, boolean creating) {
		String name = request == null ? "" : trim(request.name());
		if (name.isBlank()) {
			name = creating ? "未命名工作流" : entity.getName();
		}
		if (name.length() > 80) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称最多 80 个字");
		}
		WorkflowDtos.Graph graph = request == null || request.graph() == null ? WorkflowGraph.blank() : request.graph();
		WorkflowGraph.validate(graph);
		entity.setName(name);
		entity.setEnabled(request == null || request.enabled());
		entity.setGraphJson(write(graph));
	}

	private WorkflowEntity require(String id) {
		return workflows.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "工作流不存在"));
	}

	private WorkflowDtos.View toView(WorkflowEntity entity) {
		return new WorkflowDtos.View(
				entity.getId(),
				entity.getName(),
				entity.isEnabled(),
				parse(entity.getGraphJson()),
				entity.getLastStatus(),
				entity.getLastError(),
				entity.getLastRunAt(),
				entity.getUpdatedAt());
	}

	private WorkflowDtos.Graph parse(String json) {
		try {
			WorkflowDtos.Graph graph = mapper.readValue(json, WorkflowDtos.Graph.class);
			return graph == null ? WorkflowGraph.blank() : graph;
		}
		catch (Exception ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "工作流图不是合法 JSON");
		}
	}

	private String write(WorkflowDtos.Graph graph) {
		try {
			return mapper.writeValueAsString(graph);
		}
		catch (JacksonException ex) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法保存工作流图");
		}
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static String abbreviate(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String compact = value.replaceAll("\\s+", " ").trim();
		return compact.length() <= 500 ? compact : compact.substring(0, 500);
	}

}
