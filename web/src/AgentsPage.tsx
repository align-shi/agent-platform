import { useEffect, useRef, useState } from 'react'
import { CommentOutlined } from '@ant-design/icons'
import {
  Avatar,
  Button,
  Card,
  Checkbox,
  Empty,
  Flex,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Radio,
  Segmented,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import {
  clearAgentMemoryFacts,
  createAgent,
  deleteAgent,
  updateAgent,
  type Agent,
  type HttpConnector,
  type MemoryMode,
  type KnowledgeBase,
  type Provider,
  type RemoteMcp,
  type Skill,
} from './api'
import { AgentCallsPanel } from './CallsPage'
import { AgentChatPanel, type AgentChatHandle } from './ChatPage'

type AgentSection = 'llm' | 'memory' | 'connectors' | 'knowledge' | 'skills' | 'prompt' | 'other'

const AGENT_SECTIONS: { id: AgentSection; label: string }[] = [
  { id: 'llm', label: 'LLM 模型' },
  { id: 'memory', label: '记忆' },
  { id: 'connectors', label: '连接器' },
  { id: 'knowledge', label: '知识库' },
  { id: 'skills', label: '技能' },
  { id: 'prompt', label: '系统提示词' },
  { id: 'other', label: '其他' },
]

function AgentPreview({
  agentId,
  onError,
}: {
  agentId: string | null
  onError: (message: string) => void
}) {
  const [pane, setPane] = useState<'chat' | 'calls'>('chat')
  const [traceConversationId, setTraceConversationId] = useState('')
  const chatRef = useRef<AgentChatHandle>(null)

  useEffect(() => {
    setTraceConversationId('')
    setPane('chat')
  }, [agentId])

  return (
    <aside className="agent-side">
      <div className="agent-side-head">
        <span className="agent-side-title">
          <CommentOutlined />
          对话调试
        </span>
        <div className="agent-side-actions">
          <Segmented
            size="small"
            value={pane}
            onChange={(value) => setPane(value as 'chat' | 'calls')}
            options={[
              { label: '对话', value: 'chat' },
              { label: '轨迹', value: 'calls', disabled: !traceConversationId },
            ]}
          />
          <Button
            size="small"
            disabled={!agentId}
            onClick={() => {
              setPane('chat')
              chatRef.current?.startNew()
            }}
          >
            新对话
          </Button>
        </div>
      </div>
      <div className="agent-side-body">
        {!agentId ? (
          <div className="agent-side-empty">
            <Empty description="创建后可以在这里对话，并查看调用记录。" />
          </div>
        ) : (
          <>
            <div className="agent-side-pane" style={{ display: pane === 'chat' ? 'flex' : 'none' }}>
              <AgentChatPanel
                ref={chatRef}
                agentId={agentId}
                onError={onError}
                onHistoryOpen={(id) => {
                  setTraceConversationId(id ?? '')
                  if (!id) {
                    setPane('chat')
                  }
                }}
              />
            </div>
            {pane === 'calls' && traceConversationId ? (
              <div className="agent-side-pane">
                <AgentCallsPanel agentId={agentId} conversationId={traceConversationId} onError={onError} />
              </div>
            ) : null}
          </>
        )}
      </div>
    </aside>
  )
}

function memoryModeLabel(mode: MemoryMode | undefined) {
  if (mode === 'CROSS') {
    return '跨会话记忆'
  }
  if (mode === 'NONE') {
    return '无记忆'
  }
  return '当前会话记忆'
}

export function AgentsPage({
  agents,
  providers,
  skills,
  httpTools,
  remoteMcps,
  knowledgeBases,
  onChanged,
  onError,
}: {
  agents: Agent[]
  providers: Provider[]
  skills: Skill[]
  httpTools: HttpConnector[]
  remoteMcps: RemoteMcp[]
  knowledgeBases: KnowledgeBase[]
  onChanged: () => Promise<void>
  onError: (message: string) => void
}) {
  const chatProviders = providers.filter((item) => item.type === 'CHAT')
  const [view, setView] = useState<'list' | 'create' | string>('list')
  const [section, setSection] = useState<AgentSection>('llm')
  const [name, setName] = useState('')
  const [code, setCode] = useState('')
  const [systemPrompt, setSystemPrompt] = useState('你是公司内部助手，回答简洁、可执行。')
  const [providerId, setProviderId] = useState('')
  const [model, setModel] = useState('')
  const [skillIds, setSkillIds] = useState<string[]>([])
  const [httpToolIds, setHttpToolIds] = useState<string[]>([])
  const [mcpServerIds, setMcpServerIds] = useState<string[]>([])
  const [knowledgeBaseIds, setKnowledgeBaseIds] = useState<string[]>([])
  const [memoryMode, setMemoryMode] = useState<MemoryMode>('SESSION')
  const [summarizeWhenTokens, setSummarizeWhenTokens] = useState(8000)
  const [keepLastMessages, setKeepLastMessages] = useState(10)
  const [memoryFacts, setMemoryFacts] = useState('')
  const [saving, setSaving] = useState(false)
  const currentProvider = chatProviders.find((item) => item.id === providerId)
  const editingId = view !== 'list' && view !== 'create' ? view : null

  useEffect(() => {
    if (!providerId && chatProviders[0]) {
      setProviderId(chatProviders[0].id)
      setModel(chatProviders[0].defaultModel || '')
    }
  }, [chatProviders, providerId])

  function fill(agent: Agent) {
    setName(agent.name)
    setCode(agent.code ?? '')
    setSystemPrompt(agent.systemPrompt ?? '')
    setProviderId(agent.providerId)
    setModel(agent.model)
    setSkillIds(agent.skillIds ?? [])
    setHttpToolIds(agent.httpToolIds ?? [])
    setMcpServerIds(agent.mcpServerIds ?? [])
    setKnowledgeBaseIds(agent.knowledgeBaseIds ?? [])
    setMemoryMode(agent.memoryMode ?? 'SESSION')
    setSummarizeWhenTokens(agent.summarizeWhenTokens ?? 8000)
    setKeepLastMessages(agent.keepLastMessages ?? 10)
    setMemoryFacts(agent.memoryFacts ?? '')
  }

  function resetForm() {
    setName('')
    setCode('')
    setSystemPrompt('你是公司内部助手，回答简洁、可执行。')
    setSkillIds([])
    setHttpToolIds([])
    setMcpServerIds([])
    setKnowledgeBaseIds([])
    setMemoryMode('SESSION')
    setSummarizeWhenTokens(8000)
    setKeepLastMessages(10)
    setMemoryFacts('')
    if (chatProviders[0]) {
      setProviderId(chatProviders[0].id)
      setModel(chatProviders[0].defaultModel || '')
    }
  }

  function openAgent(agent: Agent) {
    fill(agent)
    setSection('llm')
    setView(agent.id)
  }

  function openCreate() {
    resetForm()
    setSection('llm')
    setView('create')
  }

  function backToList() {
    resetForm()
    setSection('llm')
    setView('list')
  }

  async function save() {
    if (!name.trim()) {
      onError('请填写名称')
      return
    }
    const normalizedCode = code.trim()
    if (!/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(normalizedCode)) {
      onError('唯一编码须以字母开头，只能包含字母、数字、下划线和中划线')
      return
    }
    if (!providerId || !model.trim()) {
      onError('请先填写提供商和模型')
      return
    }
    setSaving(true)
    try {
      const body = {
        name: name.trim(),
        code: normalizedCode,
        systemPrompt,
        providerId,
        model: model.trim(),
        skillIds,
        httpToolIds,
        mcpServerIds,
        knowledgeBaseIds,
        memoryMode,
        summarizeWhenTokens,
        keepLastMessages,
      }
      if (editingId) {
        const updated = await updateAgent(editingId, body)
        setMemoryFacts(updated.memoryFacts ?? '')
        await onChanged()
      } else {
        const created = await createAgent(body)
        setMemoryFacts(created.memoryFacts ?? '')
        await onChanged()
        setView(created.id)
      }
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  if (view === 'list') {
    return (
      <div>
        <Flex justify="space-between" align="flex-start" style={{ marginBottom: 20 }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              智能体
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0' }}>
              点进去配置模型、记忆和工具，右侧可以直接对话、查看调用记录。
            </Typography.Paragraph>
          </div>
          <Button type="primary" onClick={openCreate}>
            新建智能体
          </Button>
        </Flex>
        {agents.length === 0 ? (
          <Card>
            <Empty description="还没有智能体，新建后可以绑定模型和技能。" />
          </Card>
        ) : (
          <div className="card-grid">
            {agents.map((item) => (
              <Card key={item.id} hoverable onClick={() => openAgent(item)}>
                <Space vertical size={8} style={{ width: '100%' }}>
                  <Avatar style={{ background: '#1677ff' }}>{item.name.slice(0, 1)}</Avatar>
                  <Typography.Title level={5} style={{ margin: 0 }}>
                    {item.name}
                  </Typography.Title>
                  {item.code ? <Typography.Text type="secondary">{item.code}</Typography.Text> : null}
                  <Typography.Text type="secondary">
                    {item.providerName} · {item.model}
                  </Typography.Text>
                  <Space wrap>
                    <Tag>{memoryModeLabel(item.memoryMode)}</Tag>
                    <Tag>{item.skillIds?.length ? `${item.skillIds.length} 个技能` : '未绑技能'}</Tag>
                    <Tag>
                      {(item.httpToolIds?.length || 0) + (item.mcpServerIds?.length || 0)
                        ? `${(item.httpToolIds?.length || 0) + (item.mcpServerIds?.length || 0)} 个连接器`
                        : '未绑连接器'}
                    </Tag>
                  </Space>
                </Space>
              </Card>
            ))}
          </div>
        )}
      </div>
    )
  }

  return (
    <div className="agent-detail-page">
      <Flex justify="space-between" align="center" style={{ marginBottom: 12, flex: 'none' }} gap={12}>
        <Space>
          <Button onClick={backToList}>返回列表</Button>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {editingId ? name.trim() || '编辑智能体' : '新建智能体'}
          </Typography.Title>
        </Space>
        <Space>
          {editingId ? (
            <Popconfirm
              title="删除这个智能体？"
              onConfirm={() => {
                void deleteAgent(editingId)
                  .then(() => {
                    backToList()
                    return onChanged()
                  })
                  .catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
              }}
            >
              <Button danger>删除</Button>
            </Popconfirm>
          ) : null}
          <Button type="primary" loading={saving} disabled={!providerId} onClick={() => void save()}>
            {editingId ? '保存' : '创建'}
          </Button>
        </Space>
      </Flex>
      <div className="agent-workspace">
        <div className="agent-config">
          <Card title="基础信息">
            <Typography.Paragraph type="secondary">
              先填写名称和唯一编码，再配置下面的模型、记忆、连接器和提示词。
            </Typography.Paragraph>
            <Form layout="vertical">
              <Flex gap={16}>
                <Form.Item label="名称" required style={{ flex: 1, marginBottom: 0 }}>
                  <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="例如：彩票助手" />
                </Form.Item>
                <Form.Item
                  label="唯一编码"
                  required
                  extra="以字母开头，仅字母、数字、下划线或中划线，不能与其他智能体重复。"
                  style={{ flex: 1, marginBottom: 0 }}
                >
                  <Input value={code} onChange={(e) => setCode(e.target.value)} placeholder="例如：lottery" />
                </Form.Item>
              </Flex>
            </Form>
          </Card>
          <Card
            tabList={AGENT_SECTIONS.map((item) => ({ key: item.id, label: item.label }))}
            activeTabKey={section}
            onTabChange={(key) => setSection(key as AgentSection)}
          >
          {section === 'llm' ? (
            <Form layout="vertical">
              <Typography.Paragraph type="secondary">对话和摘要都走这里绑定的 Chat 提供商。</Typography.Paragraph>
              <Form.Item label="提供商">
                <Select
                  value={providerId || undefined}
                  placeholder="请先配置 Chat 提供商"
                  options={chatProviders.map((item) => ({ value: item.id, label: item.name }))}
                  onChange={(value) => {
                    const next = chatProviders.find((item) => item.id === value)
                    setProviderId(value)
                    if (next?.defaultModel) {
                      setModel(next.defaultModel)
                    }
                  }}
                />
              </Form.Item>
              <Form.Item label="模型">
                <Input value={model} onChange={(e) => setModel(e.target.value)} placeholder={currentProvider?.defaultModel || '模型名'} />
              </Form.Item>
            </Form>
          ) : null}
          {section === 'memory' ? (
            <Form layout="vertical">
              <Typography.Paragraph type="secondary">决定模型能看到多少历史，以及要不要跨会话记住要点。</Typography.Paragraph>
              <Radio.Group value={memoryMode} onChange={(e) => setMemoryMode(e.target.value)} style={{ width: '100%' }}>
                <Space vertical style={{ width: '100%' }}>
                  {(
                    [
                      ['CROSS', '跨会话记忆', '不同对话也会带上长期要点，适合记住用户偏好。'],
                      ['SESSION', '当前会话记忆', '只在本对话里记住上下文，超限后压缩成摘要。'],
                      ['NONE', '无记忆', '每次调用只看当前这句，界面里仍会留下记录。'],
                    ] as const
                  ).map(([value, title, hint]) => (
                    <Radio key={value} value={value} style={{ alignItems: 'flex-start', height: 'auto' }}>
                      <div>
                        <div>{title}</div>
                        <Typography.Text type="secondary">{hint}</Typography.Text>
                      </div>
                    </Radio>
                  ))}
                </Space>
              </Radio.Group>
              {memoryMode !== 'NONE' ? (
                <Flex gap={16} style={{ marginTop: 16 }}>
                  <Form.Item label="超过多少 token 后摘要" style={{ flex: 1 }}>
                    <InputNumber
                      min={1000}
                      max={200000}
                      value={summarizeWhenTokens}
                      onChange={(value) => setSummarizeWhenTokens(value || 8000)}
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                  <Form.Item label="摘要后保留最近几条" style={{ flex: 1 }}>
                    <InputNumber
                      min={2}
                      max={100}
                      value={keepLastMessages}
                      onChange={(value) => setKeepLastMessages(value || 10)}
                      style={{ width: '100%' }}
                    />
                  </Form.Item>
                </Flex>
              ) : null}
              {memoryMode === 'CROSS' && editingId ? (
                <>
                  <Form.Item label="已记住的跨会话要点">
                    <Input.TextArea value={memoryFacts || '还没有跨会话记忆。'} readOnly rows={4} />
                  </Form.Item>
                  <Button
                    disabled={!memoryFacts}
                    onClick={() => {
                      void clearAgentMemoryFacts(editingId)
                        .then((updated) => {
                          setMemoryFacts(updated.memoryFacts ?? '')
                          return onChanged()
                        })
                        .catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
                    }}
                  >
                    清空跨会话记忆
                  </Button>
                </>
              ) : null}
            </Form>
          ) : null}
          {section === 'connectors' ? (
            <>
              <Typography.Paragraph type="secondary">
                勾选应用内 HTTP 连接器，或远程 MCP（需先在「连接器 MCP」里登记你自己的服务地址）。内部技能运行时随绑定技能自动可用。
              </Typography.Paragraph>
              <Typography.Title level={5}>应用内连接器</Typography.Title>
              {httpTools.length === 0 ? (
                <Empty description="还没有应用内连接器，先到侧栏「连接器 MCP」登记接口。" />
              ) : (
                <Checkbox.Group value={httpToolIds} onChange={(values) => setHttpToolIds(values as string[])}>
                  <Space vertical>
                    {httpTools.map((item) => (
                      <Checkbox key={item.id} value={item.id}>
                        {item.name}
                        {item.enabled ? '' : '（停用）'}
                        <Typography.Text type="secondary">
                          {' '}
                          {item.tools?.length ? `${item.tools.length} 个工具` : '尚未添加工具'}
                          {item.description ? ` · ${item.description}` : ''}
                        </Typography.Text>
                      </Checkbox>
                    ))}
                  </Space>
                </Checkbox.Group>
              )}
              <Typography.Title level={5} style={{ marginTop: 24 }}>
                远程连接器
              </Typography.Title>
              {remoteMcps.length === 0 ? (
                <Empty description="还没有远程 MCP。先到「连接器 MCP」填入你的 MCP 地址。" />
              ) : (
                <Checkbox.Group value={mcpServerIds} onChange={(values) => setMcpServerIds(values as string[])}>
                  <Space vertical>
                    {remoteMcps.map((item) => (
                      <Checkbox key={item.id} value={item.id}>
                        {item.name}
                        {item.enabled ? '' : '（停用）'}
                        <Typography.Text type="secondary">
                          {' '}
                          {item.tools?.length ? `${item.tools.length} 个工具` : '尚未发现工具'}
                          {item.url ? ` · ${item.url}` : ''}
                        </Typography.Text>
                      </Checkbox>
                    ))}
                  </Space>
                </Checkbox.Group>
              )}
            </>
          ) : null}
          {section === 'knowledge' ? (
            <>
              <Typography.Paragraph type="secondary">
                对话时会用问题去检索这些库，把相关片段放进系统提示。库本身在侧栏「知识库」里维护。
              </Typography.Paragraph>
              {knowledgeBases.length === 0 ? (
                <Empty description="还没有知识库，先到侧栏「知识库」新建并上传文档。" />
              ) : (
                <Checkbox.Group
                  value={knowledgeBaseIds}
                  onChange={(values) => setKnowledgeBaseIds(values as string[])}
                >
                  <Space vertical>
                    {knowledgeBases.map((item) => (
                      <Checkbox key={item.id} value={item.id}>
                        {item.name}
                        <Typography.Text type="secondary">
                          {' '}
                          {item.documentCount} 篇文档
                          {item.embeddingProviderName ? ` · ${item.embeddingProviderName}` : ''}
                        </Typography.Text>
                      </Checkbox>
                    ))}
                  </Space>
                </Checkbox.Group>
              )}
            </>
          ) : null}
          {section === 'skills' ? (
            <>
              <Typography.Paragraph type="secondary">
                对话时只先注入目录，模型会按需读取说明书、参考文件并执行脚本。
              </Typography.Paragraph>
              {skills.length === 0 ? (
                <Empty description="还没有技能，先到技能页新建。" />
              ) : (
                <Checkbox.Group value={skillIds} onChange={(values) => setSkillIds(values as string[])}>
                  <Space vertical>
                    {skills.map((item) => (
                      <Checkbox key={item.id} value={item.id}>
                        {item.name}
                        <Typography.Text type="secondary"> {item.description || item.id}</Typography.Text>
                      </Checkbox>
                    ))}
                  </Space>
                </Checkbox.Group>
              )}
            </>
          ) : null}
          {section === 'prompt' ? (
            <>
              <Typography.Paragraph type="secondary">
                角色、口吻和边界。记忆摘要和技能目录会另外拼进系统消息。
              </Typography.Paragraph>
              <Input.TextArea
                value={systemPrompt}
                onChange={(e) => setSystemPrompt(e.target.value)}
                placeholder="角色、口吻、边界"
                rows={12}
              />
            </>
          ) : null}
          {section === 'other' ? <Empty description="暂时没有其他配置项。" /> : null}
          </Card>
        </div>
        <AgentPreview agentId={editingId} onError={onError} />
      </div>
    </div>
  )
}
