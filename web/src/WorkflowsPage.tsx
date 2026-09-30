import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  addEdge,
  useEdgesState,
  useNodesState,
  Handle,
  Position,
  type Connection,
  type Edge,
  type Node,
  type NodeProps,
  type NodeTypes,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import { Button, Card, Dropdown, Empty, Form, Input, Modal, Popconfirm, Select, Space, Switch, Tag, Typography, message } from 'antd'
import {
  createWorkflow,
  deleteWorkflow,
  getWorkflowRun,
  listWorkflowRuns,
  listWorkflows,
  runWorkflow,
  updateWorkflow,
  type Agent,
  type HttpConnector,
  type Provider,
  type Workflow,
  type WorkflowGraph,
  type WorkflowNodeType,
  type WorkflowRun,
  type WorkflowRunSummary,
} from './api'

type FlowData = {
  agentId?: string
  prompt?: string
  providerId?: string
  model?: string
  systemPrompt?: string
  connectorId?: string
  toolId?: string
  argumentsJson?: string
  source?: string
  operator?: string
  value?: string
}

type FlowNode = Node<FlowData, WorkflowNodeType>

const NODE_LABEL: Record<WorkflowNodeType, string> = {
  start: '开始',
  end: '结束',
  agent: '智能体',
  llm: '大模型',
  http: 'HTTP',
  condition: '条件',
}

const NODE_COLOR: Record<WorkflowNodeType, string> = {
  start: '#1677ff',
  end: '#8c8c8c',
  agent: '#13c2c2',
  llm: '#2f54eb',
  http: '#d48806',
  condition: '#722ed1',
}

type AddableType = 'agent' | 'llm' | 'http' | 'condition'

const ADDABLE: { type: AddableType; label: string }[] = [
  { type: 'agent', label: '智能体' },
  { type: 'llm', label: '大模型' },
  { type: 'http', label: 'HTTP' },
  { type: 'condition', label: '条件' },
]

type EditorApi = {
  addAfter: (sourceId: string, sourceHandle: string | null, type: AddableType) => void
}

const WorkflowEditorContext = createContext<EditorApi | null>(null)

function sameHandle(edge: Edge, handle: string | null) {
  return (edge.sourceHandle || null) === (handle || null)
}

function edgeLook(sourceHandle: string | null): Partial<Edge> {
  return {
    sourceHandle: sourceHandle || undefined,
    label: sourceHandle === 'true' ? '是' : sourceHandle === 'false' ? '否' : undefined,
    selectable: true,
    deletable: true,
    interactionWidth: 28,
  }
}

function defaultData(type: AddableType, providers: Provider[] = []): FlowData {
  if (type === 'agent') {
    return { prompt: '{{input}}' }
  }
  if (type === 'llm') {
    const first = providers.find((item) => item.type === 'CHAT')
    return {
      providerId: first?.id || '',
      model: first?.defaultModel || '',
      systemPrompt: '',
      prompt: '{{input}}',
    }
  }
  if (type === 'http') {
    return { argumentsJson: '{}' }
  }
  return { source: 'output', operator: 'contains', value: '' }
}

function NodePlus({ sourceId, sourceHandle, className }: { sourceId: string; sourceHandle?: string; className?: string }) {
  const editor = useContext(WorkflowEditorContext)
  const [open, setOpen] = useState(false)
  return (
    <Dropdown
      trigger={['click']}
      open={open}
      onOpenChange={setOpen}
      menu={{
        items: ADDABLE.map((item) => ({ key: item.type, label: item.label })),
        onClick: ({ key }) => editor?.addAfter(sourceId, sourceHandle ?? null, key as AddableType),
      }}
    >
      <button
        type="button"
        title={sourceHandle === 'true' ? '添加「是」分支' : sourceHandle === 'false' ? '添加「否」分支' : '添加下一步'}
        className={`wf-node-plus nodrag nopan${open ? ' open' : ''}${className ? ` ${className}` : ''}`}
        onClick={(event) => event.stopPropagation()}
        onMouseDown={(event) => event.stopPropagation()}
      >
        +
      </button>
    </Dropdown>
  )
}

function FlowCard({ id, type, selected, children }: { id: string; type: WorkflowNodeType; selected: boolean; children?: ReactNode }) {
  return (
    <div className={`wf-node-wrap${selected ? ' selected' : ''}`}>
      <div className={`wf-node${selected ? ' selected' : ''}`}>
        {type !== 'start' ? <Handle type="target" position={Position.Left} /> : null}
        <div className="wf-node-title" style={{ background: NODE_COLOR[type] }}>
          {NODE_LABEL[type]}
        </div>
        <div className="wf-node-body">{children || id}</div>
        {type === 'condition' ? (
          <>
            <Handle type="source" position={Position.Right} id="true" style={{ top: 28 }} />
            <Handle type="source" position={Position.Right} id="false" style={{ top: 52 }} />
          </>
        ) : type !== 'end' ? (
          <Handle type="source" position={Position.Right} />
        ) : null}
      </div>
      {type === 'condition' ? (
        <>
          <NodePlus sourceId={id} sourceHandle="true" className="wf-plus-true" />
          <NodePlus sourceId={id} sourceHandle="false" className="wf-plus-false" />
        </>
      ) : type !== 'end' ? (
        <NodePlus sourceId={id} />
      ) : null}
    </div>
  )
}

function StartNode(props: NodeProps<FlowNode>) {
  return <FlowCard id={props.id} type="start" selected={props.selected}>输入</FlowCard>
}

function EndNode(props: NodeProps<FlowNode>) {
  return <FlowCard id={props.id} type="end" selected={props.selected}>输出</FlowCard>
}

function AgentNode(props: NodeProps<FlowNode>) {
  return <FlowCard id={props.id} type="agent" selected={props.selected}>{props.data.agentId ? '已选智能体' : '未选择'}</FlowCard>
}

function LlmNode(props: NodeProps<FlowNode>) {
  return <FlowCard id={props.id} type="llm" selected={props.selected}>{props.data.model || '未选择'}</FlowCard>
}

function HttpNode(props: NodeProps<FlowNode>) {
  return <FlowCard id={props.id} type="http" selected={props.selected}>{props.data.toolId ? '已选工具' : '未选择'}</FlowCard>
}

function ConditionNode(props: NodeProps<FlowNode>) {
  return (
    <FlowCard id={props.id} type="condition" selected={props.selected}>
      <div>是 / 否</div>
    </FlowCard>
  )
}

const NODE_TYPES: NodeTypes = {
  start: StartNode,
  end: EndNode,
  agent: AgentNode,
  llm: LlmNode,
  http: HttpNode,
  condition: ConditionNode,
}

function toFlow(graph: WorkflowGraph): { nodes: FlowNode[]; edges: Edge[] } {
  return {
    nodes: (graph.nodes || []).map((node) => ({
      id: node.id,
      type: node.type,
      position: { x: node.x, y: node.y },
      data: node.data || {},
      deletable: node.type !== 'start' && node.type !== 'end',
    })),
    edges: (graph.edges || []).map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      sourceHandle: edge.branch || undefined,
      label: edge.branch === 'true' ? '是' : edge.branch === 'false' ? '否' : undefined,
      selectable: true,
      deletable: true,
      interactionWidth: 28,
    })),
  }
}

function toGraph(nodes: FlowNode[], edges: Edge[]): WorkflowGraph {
  return {
    nodes: nodes.map((node) => ({
      id: node.id,
      type: (node.type || 'agent') as WorkflowNodeType,
      x: node.position.x,
      y: node.position.y,
      data: Object.fromEntries(
        Object.entries(node.data || {})
          .filter(([, value]) => value != null && String(value) !== '')
          .map(([key, value]) => [key, String(value)]),
      ),
    })),
    edges: edges.map((edge) => ({
      id: edge.id,
      source: edge.source,
      target: edge.target,
      branch: edge.sourceHandle || null,
    })),
  }
}

function nextId(prefix: string) {
  return `${prefix}-${Math.random().toString(36).slice(2, 8)}`
}

export function WorkflowsPage({
  agents,
  providers,
  httpTools,
  onChanged,
  onError,
}: {
  agents: Agent[]
  providers: Provider[]
  httpTools: HttpConnector[]
  onChanged?: () => Promise<void>
  onError: (message: string) => void
}) {
  const [items, setItems] = useState<Workflow[]>([])
  const [loaded, setLoaded] = useState(false)
  const [editing, setEditing] = useState<Workflow | null>(null)
  const [name, setName] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [saving, setSaving] = useState(false)
  const [running, setRunning] = useState(false)
  const [runOpen, setRunOpen] = useState(false)
  const [runInput, setRunInput] = useState('')
  const [runResult, setRunResult] = useState<WorkflowRun | null>(null)
  const [runs, setRuns] = useState<WorkflowRunSummary[]>([])
  const [historyRun, setHistoryRun] = useState<WorkflowRun | null>(null)
  const [nodes, setNodes, onNodesChange] = useNodesState<FlowNode>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null)
  const [form] = Form.useForm<FlowData>()

  const selected = nodes.find((node) => node.id === selectedId) || null
  const highlighted = historyRun ?? runResult
  const displayNodes = useMemo(() => {
    const statusById = new Map((highlighted?.steps || []).map((step) => [step.nodeId, step.status]))
    return nodes.map((node) => {
      const status = statusById.get(node.id)
      return {
        ...node,
        className: status === 'failed' ? 'wf-ran-fail' : status === 'succeeded' ? 'wf-ran-ok' : undefined,
      }
    })
  }, [highlighted, nodes])
  const chatProviders = useMemo(() => providers.filter((item) => item.type === 'CHAT'), [providers])
  const httpOptions = useMemo(
    () =>
      httpTools.flatMap((connector) =>
        (connector.tools || []).map((tool) => ({
          value: `${connector.id}:${tool.id}`,
          label: `${connector.name} / ${tool.name}`,
        })),
      ),
    [httpTools],
  )

  async function refreshList() {
    const next = await listWorkflows()
    setItems(next)
    setLoaded(true)
  }

  useEffect(() => {
    void refreshList().catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
  }, [onError])

  const openEditor = useCallback((workflow: Workflow) => {
    const flow = toFlow(workflow.graph)
    setEditing(workflow)
    setName(workflow.name)
    setEnabled(workflow.enabled)
    setNodes(flow.nodes)
    setEdges(flow.edges)
    setSelectedId(null)
    setSelectedEdgeId(null)
    setRunResult(null)
    setHistoryRun(null)
    setRuns([])
    form.resetFields()
    void loadRuns(workflow.id)
  }, [form, setEdges, setNodes])

  async function loadRuns(workflowId: string) {
    try {
      setRuns(await listWorkflowRuns(workflowId))
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function openHistory(runId: string) {
    if (!editing) {
      return
    }
    try {
      const detail = await getWorkflowRun(editing.id, runId)
      setHistoryRun(detail)
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  function closeEditor() {
    setEditing(null)
    void refreshList().catch(() => undefined)
  }

  async function onCreate() {
    try {
      const created = await createWorkflow({ name: '未命名工作流', enabled: true })
      message.success('已创建工作流')
      await onChanged?.()
      openEditor(created)
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function onSave() {
    if (!editing) {
      return
    }
    setSaving(true)
    try {
      const saved = await updateWorkflow(editing.id, { name, enabled, graph: toGraph(nodes, edges) })
      setEditing(saved)
      message.success('工作流已保存')
      await onChanged?.()
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  async function onDelete(workflow: Workflow) {
    try {
      await deleteWorkflow(workflow.id)
      message.success('已删除')
      if (editing?.id === workflow.id) {
        setEditing(null)
      }
      await onChanged?.()
      await refreshList()
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function onRun() {
    if (!editing) {
      return
    }
    setRunning(true)
    try {
      await updateWorkflow(editing.id, { name, enabled, graph: toGraph(nodes, edges) })
      const result = await runWorkflow(editing.id, runInput)
      setRunResult(result)
      setHistoryRun(result)
      await loadRuns(editing.id)
      if (result.status === 'succeeded') {
        message.success('运行完成')
      } else {
        onError(result.error || '工作流运行失败')
      }
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setRunning(false)
    }
  }

  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((current) =>
        addEdge(
          {
            ...connection,
            id: nextId('e'),
            label: connection.sourceHandle === 'true' ? '是' : connection.sourceHandle === 'false' ? '否' : undefined,
            selectable: true,
            deletable: true,
            interactionWidth: 28,
          },
          current,
        ),
      )
    },
    [setEdges],
  )

  const addAfter = useCallback(
    (sourceId: string, sourceHandle: string | null, type: AddableType) => {
      const source = nodes.find((node) => node.id === sourceId)
      if (!source) {
        return
      }
      const outgoing = edges.find((edge) => edge.source === sourceId && sameHandle(edge, sourceHandle))
      const oldTarget = outgoing ? nodes.find((node) => node.id === outgoing.target) : undefined
      const endId = nodes.find((node) => node.type === 'end')?.id
      const id = nextId(type)
      const shiftY = sourceHandle === 'false' ? 48 : sourceHandle === 'true' ? -48 : 0
      const position =
        outgoing && oldTarget
          ? {
              x: (source.position.x + oldTarget.position.x) / 2,
              y: (source.position.y + oldTarget.position.y) / 2 + shiftY,
            }
          : { x: source.position.x + 220, y: source.position.y + shiftY }
      setNodes((current) => [
        ...current,
        { id, type, position, data: defaultData(type, providers), deletable: true },
      ])
      setEdges((current) => {
        let next = outgoing ? current.filter((edge) => edge.id !== outgoing.id) : current
        next = [
          ...next,
          { id: nextId('e'), source: sourceId, target: id, ...edgeLook(sourceHandle) },
        ]
        if (type === 'condition') {
          const yesTarget = outgoing?.target || endId
          if (yesTarget) {
            next = [...next, { id: nextId('e'), source: id, target: yesTarget, ...edgeLook('true') }]
          }
          if (endId) {
            next = [...next, { id: nextId('e'), source: id, target: endId, ...edgeLook('false') }]
          }
        } else if (outgoing) {
          next = [...next, { id: nextId('e'), source: id, target: outgoing.target, ...edgeLook(null) }]
        } else if (endId) {
          next = [...next, { id: nextId('e'), source: id, target: endId, ...edgeLook(null) }]
        }
        return next
      })
      setSelectedId(id)
      setSelectedEdgeId(null)
    },
    [edges, nodes, providers, setEdges, setNodes],
  )
  const editorApi = useMemo(() => ({ addAfter }), [addAfter])

  function removeSelectedEdge() {
    if (!selectedEdgeId) {
      return
    }
    setEdges((current) => current.filter((edge) => edge.id !== selectedEdgeId))
    setSelectedEdgeId(null)
  }

  function applySelected(values: FlowData) {
    if (!selected) {
      return
    }
    const data = { ...values }
    if (values.connectorId && values.connectorId.includes(':')) {
      const [connectorId, toolId] = values.connectorId.split(':')
      data.connectorId = connectorId
      data.toolId = toolId
    }
    if (selected.type === 'llm' && values.providerId && values.providerId !== selected.data.providerId) {
      const provider = chatProviders.find((item) => item.id === values.providerId)
      if (provider?.defaultModel) {
        data.model = provider.defaultModel
        form.setFieldValue('model', provider.defaultModel)
      }
    }
    setNodes((current) => current.map((node) => (node.id === selected.id ? { ...node, data } : node)))
  }

  useEffect(() => {
    if (!selected) {
      form.resetFields()
      return
    }
    const data = { ...selected.data }
    if (data.connectorId && data.toolId) {
      data.connectorId = `${data.connectorId}:${data.toolId}`
    }
    form.setFieldsValue(data)
  }, [selected, form])

  if (editing) {
    return (
      <div className="workflow-page">
        <div className="workflow-bar">
          <Space>
            <Button onClick={closeEditor}>返回列表</Button>
            <Input value={name} onChange={(event) => setName(event.target.value)} style={{ width: 220 }} />
            <Switch checked={enabled} onChange={setEnabled} checkedChildren="启用" unCheckedChildren="停用" />
          </Space>
          <Space>
            <Button onClick={() => { setRunOpen(true); setRunResult(null) }}>运行</Button>
            <Button type="primary" loading={saving} onClick={() => void onSave()}>
              保存
            </Button>
          </Space>
        </div>
        <div className="workflow-workspace">
          <div className="workflow-canvas">
            <WorkflowEditorContext.Provider value={editorApi}>
            <ReactFlow
              nodes={displayNodes}
              edges={edges}
              nodeTypes={NODE_TYPES}
              onNodesChange={onNodesChange}
              onEdgesChange={onEdgesChange}
              onConnect={onConnect}
              onNodeClick={(_, node) => {
                setSelectedId(node.id)
                setSelectedEdgeId(null)
              }}
              onEdgeClick={(_, edge) => {
                setSelectedEdgeId(edge.id)
                setSelectedId(null)
              }}
              onEdgeDoubleClick={(_, edge) => {
                setEdges((current) => current.filter((item) => item.id !== edge.id))
                setSelectedEdgeId(null)
              }}
              onPaneClick={() => {
                setSelectedId(null)
                setSelectedEdgeId(null)
              }}
              deleteKeyCode={['Backspace', 'Delete']}
              defaultEdgeOptions={{ selectable: true, deletable: true, interactionWidth: 28 }}
              elevateEdgesOnSelect
              fitView
            >
              <Background />
              <Controls />
              <MiniMap />
            </ReactFlow>
            </WorkflowEditorContext.Provider>
          </div>
          <div className="workflow-inspector">
            {selectedEdgeId ? (
              <>
                <Typography.Title level={5} style={{ marginTop: 0 }}>
                  连线
                </Typography.Title>
                <Typography.Paragraph type="secondary">点「删除」或按 Delete 去掉这条线。</Typography.Paragraph>
                <Button danger onClick={removeSelectedEdge}>
                  删除连线
                </Button>
              </>
            ) : selected ? (
              <Form form={form} layout="vertical" onValuesChange={(_, values) => applySelected(values)}>
                <Typography.Title level={5} style={{ marginTop: 0 }}>
                  {NODE_LABEL[selected.type || 'agent']}
                </Typography.Title>
                {selected.type === 'agent' ? (
                  <>
                    <Form.Item name="agentId" label="智能体">
                      <Select options={agents.map((agent) => ({ value: agent.id, label: agent.name }))} placeholder="选择智能体" />
                    </Form.Item>
                    <Form.Item name="prompt" label="提示词" extra="可用 {{input}} 和 {{output}}">
                      <Input.TextArea rows={4} />
                    </Form.Item>
                  </>
                ) : null}
                {selected.type === 'llm' ? (
                  <>
                    <Form.Item name="providerId" label="模型提供商">
                      <Select
                        options={chatProviders.map((item) => ({ value: item.id, label: item.name }))}
                        placeholder="选择提供商"
                      />
                    </Form.Item>
                    <Form.Item name="model" label="模型">
                      <Input placeholder="例如 gpt-4.1-mini" />
                    </Form.Item>
                    <Form.Item name="systemPrompt" label="系统提示词">
                      <Input.TextArea rows={3} placeholder="可选，角色和约束" />
                    </Form.Item>
                    <Form.Item name="prompt" label="用户提示词" extra="可用 {{input}} 和 {{output}}">
                      <Input.TextArea rows={4} />
                    </Form.Item>
                  </>
                ) : null}
                {selected.type === 'http' ? (
                  <>
                    <Form.Item name="connectorId" label="HTTP 工具">
                      <Select options={httpOptions} placeholder="选择工具" />
                    </Form.Item>
                    <Form.Item name="argumentsJson" label="参数 JSON" extra="可用 {{input}} 和 {{output}}">
                      <Input.TextArea rows={5} />
                    </Form.Item>
                  </>
                ) : null}
                {selected.type === 'condition' ? (
                  <>
                    <Form.Item name="source" label="判断对象">
                      <Select
                        options={[
                          { value: 'output', label: '上一节点输出' },
                          { value: 'input', label: '工作流输入' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name="operator" label="条件">
                      <Select
                        options={[
                          { value: 'contains', label: '包含' },
                          { value: 'equals', label: '等于' },
                          { value: 'not_empty', label: '非空' },
                        ]}
                      />
                    </Form.Item>
                    <Form.Item name="value" label="比较值">
                      <Input />
                    </Form.Item>
                  </>
                ) : null}
                {selected.type === 'start' || selected.type === 'end' ? (
                  <Typography.Paragraph type="secondary">这个节点不用配置。</Typography.Paragraph>
                ) : null}
              </Form>
            ) : (
              <Typography.Paragraph type="secondary">
                点节点右侧的 + 选择下一步。条件节点有「是」「否」两个加号。点选节点后在这里配置。
              </Typography.Paragraph>
            )}
            <div style={{ marginTop: 24, paddingTop: 16, borderTop: '1px solid #f0f0f0' }}>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                运行记录
              </Typography.Title>
              {runs.length === 0 ? (
                <Typography.Paragraph type="secondary">还没有运行过。点右上角「运行」试一次。</Typography.Paragraph>
              ) : (
                runs.map((item) => (
                  <button
                    key={item.id}
                    type="button"
                    className={`workflow-run-item${historyRun?.id === item.id ? ' active' : ''}`}
                    onClick={() => void openHistory(item.id)}
                  >
                    <div>
                      <Tag color={item.status === 'succeeded' ? 'green' : 'red'}>
                        {item.status === 'succeeded' ? '成功' : '失败'}
                      </Tag>
                      <Typography.Text type="secondary">
                        {item.createdAt ? new Date(item.createdAt).toLocaleString() : ''}
                      </Typography.Text>
                    </div>
                    <Typography.Text type="secondary" ellipsis>
                      {item.input || item.error || '没有输入'}
                    </Typography.Text>
                  </button>
                ))
              )}
              {highlighted?.steps?.length ? (
                <div style={{ marginTop: 12 }}>
                  {highlighted.steps.map((step) => (
                    <Typography.Paragraph key={`${step.nodeId}-${step.nodeType}`} type="secondary" style={{ marginBottom: 4 }}>
                      {NODE_LABEL[step.nodeType as WorkflowNodeType] || step.nodeType} · {step.status}
                      {step.error ? ` · ${step.error}` : ''}
                    </Typography.Paragraph>
                  ))}
                </div>
              ) : null}
            </div>
          </div>
        </div>
        <Modal
          title="运行工作流"
          open={runOpen}
          confirmLoading={running}
          okText="运行"
          cancelText="关闭"
          onOk={() => void onRun()}
          onCancel={() => setRunOpen(false)}
          width={640}
        >
          <Input.TextArea rows={4} value={runInput} onChange={(event) => setRunInput(event.target.value)} placeholder="输入这次运行要用的文字" />
          {runResult ? (
            <div style={{ marginTop: 16 }}>
              <Tag color={runResult.status === 'succeeded' ? 'green' : 'red'}>{runResult.status === 'succeeded' ? '成功' : '失败'}</Tag>
              <Typography.Paragraph style={{ marginTop: 8, whiteSpace: 'pre-wrap' }}>{runResult.output || runResult.error || '没有输出'}</Typography.Paragraph>
              {runResult.steps?.map((step) => (
                <Typography.Paragraph key={`${step.nodeId}-${step.nodeType}`} type="secondary" style={{ marginBottom: 4 }}>
                  {NODE_LABEL[step.nodeType as WorkflowNodeType] || step.nodeType} · {step.status}
                  {step.error ? ` · ${step.error}` : ''}
                </Typography.Paragraph>
              ))}
            </div>
          ) : null}
        </Modal>
      </div>
    )
  }

  return (
    <div>
      <Typography.Title level={3} style={{ margin: 0 }}>
        工作流
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ margin: '8px 0 16px' }}>
        把智能体、大模型、HTTP 工具和条件连成一条固定路径。在节点右侧点 + 添加下一步，保存后再运行。
      </Typography.Paragraph>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Typography.Title level={5} style={{ margin: 0 }}>
          工作流
        </Typography.Title>
        <Button type="primary" onClick={() => void onCreate()}>
          添加工作流
        </Button>
      </div>
      {!loaded ? null : items.length === 0 ? (
        <Empty description="还没有工作流" />
      ) : (
        <div className="card-grid">
          {items.map((item) => (
            <Card key={item.id} hoverable onClick={() => openEditor(item)}>
              <Space vertical size={8} style={{ width: '100%' }}>
                <Typography.Title level={5} style={{ margin: 0 }}>
                  {item.name}
                </Typography.Title>
                <Space>
                  {item.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>}
                  {item.lastStatus ? (
                    <Tag color={item.lastStatus === 'succeeded' ? 'blue' : 'red'}>
                      {item.lastStatus === 'succeeded' ? '上次成功' : '上次失败'}
                    </Tag>
                  ) : null}
                </Space>
                <Typography.Text type="secondary">
                  {item.lastRunAt ? `最近运行 ${new Date(item.lastRunAt).toLocaleString()}` : '还没有运行过'}
                </Typography.Text>
                <Space>
                  <Button
                    onClick={(event) => {
                      event.stopPropagation()
                      openEditor(item)
                    }}
                  >
                    编辑
                  </Button>
                  <Popconfirm
                    title="删除这个工作流？"
                    okText="删除"
                    cancelText="取消"
                    onConfirm={(event) => {
                      event?.stopPropagation()
                      void onDelete(item)
                    }}
                  >
                    <Button danger onClick={(event) => event.stopPropagation()}>
                      删除
                    </Button>
                  </Popconfirm>
                </Space>
              </Space>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
