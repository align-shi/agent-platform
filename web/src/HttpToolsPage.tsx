import { useState } from 'react'
import {
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd'
import { DeleteOutlined } from '@ant-design/icons'
import {
  createHttpTool,
  deleteHttpTool,
  tryHttpTool,
  updateHttpTool,
  type HttpAuthType,
  type HttpConnector,
  type HttpConnectorTool,
  type HttpToolParam,
} from './api'

const MAX_TOOLS = 30
const SAVED_SECRET_MASK = '••••••••••••••••'

function emptyParam(): HttpToolParam {
  return { name: '', in: 'query', type: 'string', required: true, description: '' }
}

function emptyTool(): HttpConnectorTool {
  return {
    id: `local-${crypto.randomUUID()}`,
    name: '',
    toolName: '',
    description: '',
    method: 'GET',
    url: 'https://',
    enabled: true,
    parameters: [],
  }
}

function authLabel(type: HttpAuthType) {
  if (type === 'BEARER') {
    return 'Bearer'
  }
  if (type === 'HEADER') {
    return '自定义 Header'
  }
  return '无鉴权'
}

export function HttpToolsSection({
  tools,
  onChanged,
  onError,
}: {
  tools: HttpConnector[]
  onChanged: () => Promise<void>
  onError: (message: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [authType, setAuthType] = useState<HttpAuthType>('NONE')
  const [authHeader, setAuthHeader] = useState('Authorization')
  const [secret, setSecret] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [forwardCredentials, setForwardCredentials] = useState(false)
  const [timeoutMs, setTimeoutMs] = useState(15000)
  const [draftTools, setDraftTools] = useState<HttpConnectorTool[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [trying, setTrying] = useState(false)
  const [tryOutput, setTryOutput] = useState('')
  const editing = tools.find((item) => item.id === editingId)
  const selected = draftTools.find((item) => item.id === selectedId) ?? null

  function fill(item: HttpConnector) {
    setEditingId(item.id)
    setName(item.name)
    setDescription(item.description ?? '')
    setAuthType(item.authType)
    setAuthHeader(item.authHeader || 'Authorization')
    setSecret(item.secretConfigured ? SAVED_SECRET_MASK : '')
    setEnabled(item.enabled)
    setForwardCredentials(item.forwardCredentials)
    setTimeoutMs(item.timeoutMs || 15000)
    const nextTools = item.tools?.length ? item.tools.map((tool) => ({ ...tool, parameters: tool.parameters ?? [] })) : []
    setDraftTools(nextTools)
    setSelectedId(nextTools[0]?.id ?? null)
    setTryOutput('')
  }

  function resetForm() {
    setEditingId(null)
    setName('')
    setDescription('')
    setAuthType('NONE')
    setAuthHeader('Authorization')
    setSecret('')
    setEnabled(true)
    setForwardCredentials(false)
    setTimeoutMs(15000)
    setDraftTools([])
    setSelectedId(null)
    setTryOutput('')
  }

  function openCreate() {
    resetForm()
    setOpen(true)
  }

  function openEdit(item: HttpConnector) {
    fill(item)
    setOpen(true)
  }

  function closeDialog() {
    if (saving) {
      return
    }
    setOpen(false)
    resetForm()
  }

  function addTool() {
    if (draftTools.length >= MAX_TOOLS) {
      onError(`每个连接器最多 ${MAX_TOOLS} 个工具`)
      return
    }
    const next = emptyTool()
    setDraftTools((current) => [...current, next])
    setSelectedId(next.id)
    setTryOutput('')
  }

  function patchTool(id: string, patch: Partial<HttpConnectorTool>) {
    setDraftTools((current) => current.map((item) => (item.id === id ? { ...item, ...patch } : item)))
  }

  function updateParam(index: number, patch: Partial<HttpToolParam>) {
    if (!selected) {
      return
    }
    const parameters = selected.parameters.map((item, i) => (i === index ? { ...item, ...patch } : item))
    patchTool(selected.id, { parameters })
  }

  function removeTool(id: string) {
    const next = draftTools.filter((item) => item.id !== id)
    setDraftTools(next)
    setSelectedId((current) => {
      if (current !== id) {
        return current
      }
      return next[0]?.id ?? null
    })
    setTryOutput('')
  }

  async function onSubmit() {
    if (!name.trim()) {
      onError('请填写显示名')
      return
    }
    if (authType !== 'NONE') {
      const hasNewSecret = secret.trim() !== '' && secret !== SAVED_SECRET_MASK
      if (!editingId && !hasNewSecret) {
        onError('该鉴权方式需要填写密钥')
        return
      }
      if (editingId && !editing?.secretConfigured && !hasNewSecret) {
        onError('该鉴权方式需要填写密钥')
        return
      }
    }
    for (const tool of draftTools) {
      if (!tool.name.trim() || !tool.toolName.trim() || !tool.url.trim()) {
        onError('每个 Tool 都需要填写名称、工具名和地址')
        return
      }
    }
    setSaving(true)
    try {
      const body = {
        name: name.trim(),
        description,
        protocol: 'DECLARATIVE',
        authType,
        authHeader,
        timeoutMs,
        enabled,
        forwardCredentials,
        tools: draftTools.map((tool) => ({
          ...(tool.id.startsWith('local-') ? {} : { id: tool.id }),
          name: tool.name.trim(),
          toolName: tool.toolName.trim(),
          description: tool.description,
          method: tool.method,
          url: tool.url.trim(),
          enabled: tool.enabled,
          parameters: tool.parameters.filter((item) => item.name.trim()).map((item) => ({
            name: item.name.trim(),
            in: item.in,
            type: item.type,
            required: item.required,
            description: item.description,
          })),
        })),
        ...(secret.trim() && secret !== SAVED_SECRET_MASK ? { secret: secret.trim() } : {}),
      }
      if (editingId) {
        await updateHttpTool(editingId, body)
        message.success('连接器已保存')
      } else {
        await createHttpTool(body)
        message.success('连接器已创建')
      }
      setOpen(false)
      resetForm()
      await onChanged()
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  async function onTry() {
    if (!editingId || !selected || selected.id.startsWith('local-')) {
      onError('请先保存连接器和该 Tool，再试连接')
      return
    }
    setTrying(true)
    try {
      const args: Record<string, string> = {}
      for (const param of selected.parameters) {
        if (param.name.trim()) {
          args[param.name.trim()] = param.type === 'boolean' ? 'true' : 'demo'
        }
      }
      const liveSecret = secret.trim() && secret !== SAVED_SECRET_MASK ? secret.trim() : undefined
      setTryOutput(await tryHttpTool(editingId, selected.id, args, liveSecret))
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setTrying(false)
    }
  }

  return (
    <>
      <Flex justify="space-between" align="flex-start" style={{ marginBottom: 12 }}>
        <div>
          <Typography.Title level={5} style={{ margin: 0 }}>
            应用内连接器
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
            列表只展示连接器头信息。点进去再配一组相关 HTTP 工具，智能体勾选整个连接器即可。
          </Typography.Paragraph>
        </div>
        <Button type="primary" onClick={openCreate}>
          创建
        </Button>
      </Flex>
      {tools.length === 0 ? (
        <Card>
          <Empty description="还没有应用内连接器。创建一个后，在里面添加多个 Tool。" />
        </Card>
      ) : (
        <div className="card-grid">
          {tools.map((item) => (
            <Card
              key={item.id}
              hoverable
              onClick={() => openEdit(item)}
              actions={[
                <Button
                  key="edit"
                  type="link"
                  onClick={(event) => {
                    event.stopPropagation()
                    openEdit(item)
                  }}
                >
                  编辑
                </Button>,
                <Popconfirm
                  key="del"
                  title="删除这个连接器及其全部 Tool？"
                  onConfirm={(event) => {
                    event?.stopPropagation()
                    void deleteHttpTool(item.id)
                      .then(() => {
                        if (editingId === item.id) {
                          closeDialog()
                        }
                        return onChanged()
                      })
                      .catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
                  }}
                >
                  <Button type="link" danger onClick={(event) => event.stopPropagation()}>
                    删除
                  </Button>
                </Popconfirm>,
              ]}
            >
              <Space vertical size={8} style={{ width: '100%' }}>
                <Tag color={item.enabled ? 'blue' : 'default'}>声明式</Tag>
                <Typography.Title level={5} style={{ margin: 0 }}>
                  {item.name}
                  {item.enabled ? '' : '（停用）'}
                </Typography.Title>
                <Typography.Text type="secondary" className="http-card-url">
                  {item.description || '未填写描述'}
                </Typography.Text>
                <Space wrap>
                  <Tag>{authLabel(item.authType)}</Tag>
                  <Tag>{`${item.tools?.length ?? 0} 个工具`}</Tag>
                </Space>
              </Space>
            </Card>
          ))}
        </div>
      )}
      <Modal
        className="connector-modal"
        title={editingId ? '编辑声明式连接器' : '新建声明式连接器'}
        open={open}
        mask={{ closable: false }}
        keyboard={false}
        onCancel={closeDialog}
        width={1080}
        styles={{ body: { paddingTop: 12, paddingBottom: 8, maxHeight: '78vh', overflow: 'auto' } }}
        footer={
          <Space>
            <Button onClick={closeDialog}>取消</Button>
            <Button type="primary" loading={saving} onClick={() => void onSubmit()}>
              {editingId ? '保存' : '创建'}
            </Button>
          </Space>
        }
      >
        <Form layout="vertical" className="connector-form">
          <Form.Item label="显示名" required style={{ marginBottom: 8 }}>
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="列表中展示的名称，例如：SRM 工具集" />
          </Form.Item>
          <Form.Item label="描述" style={{ marginBottom: 8 }}>
            <Input.TextArea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="简要说明此声明式连接器的用途，例如：SRM 物料查询与状态调整"
              rows={3}
            />
          </Form.Item>
          <Flex gap={12} wrap>
            <Form.Item label="认证类型" style={{ width: 200, marginBottom: 8 }}>
              <Select
                value={authType}
                onChange={(value) => setAuthType(value)}
                options={[
                  { value: 'NONE', label: 'none' },
                  { value: 'BEARER', label: 'Bearer Token' },
                  { value: 'HEADER', label: '自定义 Header' },
                ]}
              />
            </Form.Item>
            {authType === 'HEADER' ? (
              <Form.Item label="Header 名" style={{ width: 220, marginBottom: 8 }}>
                <Input value={authHeader} onChange={(e) => setAuthHeader(e.target.value)} placeholder="X-API-Key" />
              </Form.Item>
            ) : null}
            {authType !== 'NONE' ? (
              <Form.Item label="密钥" style={{ flex: 1, minWidth: 280, marginBottom: 8 }}>
                <Input.Password
                  value={secret}
                  onChange={(e) => setSecret(e.target.value)}
                  placeholder={editingId ? '' : '必填'}
                />
              </Form.Item>
            ) : null}
            <Form.Item label="选项" style={{ marginBottom: 8 }}>
              <Space size={8}>
                <Switch size="small" checked={enabled} onChange={setEnabled} />
                <span>启用</span>
                <Switch size="small" checked={forwardCredentials} onChange={setForwardCredentials} />
                <span>可信凭证转发</span>
              </Space>
            </Form.Item>
          </Flex>
        </Form>
        <Flex justify="space-between" align="center" style={{ margin: '4px 0 6px' }}>
          <Typography.Text strong>Tools</Typography.Text>
          <Typography.Text type="success">
            {draftTools.length} / {MAX_TOOLS}
          </Typography.Text>
        </Flex>
        <div className="connector-tools">
          <div className="connector-tool-list">
            <Flex justify="space-between" align="center" style={{ padding: '6px 10px', borderBottom: '1px solid #f0f0f0' }}>
              <Typography.Text>工具列表</Typography.Text>
              <Button size="small" onClick={addTool}>
                + 新建
              </Button>
            </Flex>
            {draftTools.length === 0 ? (
              <div style={{ padding: 16 }}>
                <Empty description={'暂无 tool，点击「新建」'} image={Empty.PRESENTED_IMAGE_SIMPLE} />
              </div>
            ) : (
              draftTools.map((item) => (
                <div
                  key={item.id}
                  className={item.id === selectedId ? 'connector-tool-row active' : 'connector-tool-row'}
                >
                  <button
                    type="button"
                    className="tree-item file"
                    onClick={() => {
                      setSelectedId(item.id)
                      setTryOutput('')
                    }}
                  >
                    {item.name.trim() || item.toolName.trim() || '未命名 Tool'}
                  </button>
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<DeleteOutlined />}
                    onClick={(event) => {
                      event.stopPropagation()
                      removeTool(item.id)
                    }}
                  />
                </div>
              ))
            )}
          </div>
          <div className="connector-tool-editor">
            {selected ? (
              <Form layout="vertical" className="connector-form">
                <Flex gap={12}>
                  <Form.Item label="显示名称" required style={{ flex: 1, marginBottom: 8 }}>
                    <Input value={selected.name} onChange={(e) => patchTool(selected.id, { name: e.target.value })} placeholder="查询订单" />
                  </Form.Item>
                  <Form.Item label="工具名" required style={{ flex: 1, marginBottom: 8 }}>
                    <Input value={selected.toolName} onChange={(e) => patchTool(selected.id, { toolName: e.target.value })} placeholder="query_order" />
                  </Form.Item>
                </Flex>
                <Form.Item label="描述" style={{ marginBottom: 8 }}>
                  <Input.TextArea
                    value={selected.description}
                    onChange={(e) => patchTool(selected.id, { description: e.target.value })}
                    placeholder="这个 Tool 做什么"
                    rows={3}
                  />
                </Form.Item>
                <Flex gap={12}>
                  <Form.Item label="方法" style={{ width: 120, marginBottom: 8 }}>
                    <Select
                      value={selected.method}
                      onChange={(value) => patchTool(selected.id, { method: value })}
                      options={['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].map((item) => ({ value: item, label: item }))}
                    />
                  </Form.Item>
                  <Form.Item label="接口地址" extra="路径参数写成 {name}" required style={{ flex: 1, marginBottom: 8 }}>
                    <Input value={selected.url} onChange={(e) => patchTool(selected.id, { url: e.target.value })} placeholder="https://api.example.com/orders/{orderId}" />
                  </Form.Item>
                  <Form.Item label="启用" style={{ width: 72, marginBottom: 8 }}>
                    <Switch size="small" checked={selected.enabled} onChange={(checked) => patchTool(selected.id, { enabled: checked })} />
                  </Form.Item>
                </Flex>
                <Flex justify="space-between" align="center" style={{ marginBottom: 6 }}>
                  <Typography.Text>请求参数</Typography.Text>
                  <Button
                    size="small"
                    onClick={() => patchTool(selected.id, { parameters: [...selected.parameters, emptyParam()] })}
                  >
                    添加参数
                  </Button>
                </Flex>
                {selected.parameters.length === 0 ? (
                  <Typography.Paragraph type="secondary" style={{ margin: '0 0 8px' }}>
                    没有参数也可以，适合无入参的 GET。
                  </Typography.Paragraph>
                ) : null}
                {selected.parameters.map((item, index) => (
                  <Flex key={index} gap={8} style={{ marginBottom: 8 }} wrap>
                    <Input
                      style={{ width: 140 }}
                      value={item.name}
                      onChange={(e) => updateParam(index, { name: e.target.value })}
                      placeholder="参数名"
                    />
                    <Select
                      style={{ width: 110 }}
                      value={item.in}
                      onChange={(value) => updateParam(index, { in: value })}
                      options={['query', 'path', 'header', 'body'].map((value) => ({ value, label: value }))}
                    />
                    <Select
                      style={{ width: 110 }}
                      value={item.type}
                      onChange={(value) => updateParam(index, { type: value })}
                      options={['string', 'number', 'integer', 'boolean'].map((value) => ({ value, label: value }))}
                    />
                    <Switch
                      size="small"
                      checkedChildren="必填"
                      unCheckedChildren="可选"
                      checked={item.required}
                      onChange={(checked) => updateParam(index, { required: checked })}
                    />
                    <Input
                      style={{ flex: 1, minWidth: 180 }}
                      value={item.description}
                      onChange={(e) => updateParam(index, { description: e.target.value })}
                      placeholder="说明"
                    />
                    <Button
                      danger
                      type="text"
                      size="small"
                      style={{ flexShrink: 0 }}
                      onClick={() =>
                        patchTool(selected.id, { parameters: selected.parameters.filter((_, i) => i !== index) })
                      }
                    >
                      删除
                    </Button>
                  </Flex>
                ))}
                <Flex justify="flex-end" align="center" style={{ marginTop: 4 }}>
                  <Button size="small" disabled={!editingId || selected.id.startsWith('local-')} loading={trying} onClick={() => void onTry()}>
                    试连接
                  </Button>
                </Flex>
                {tryOutput ? (
                  <Input.TextArea
                    value={tryOutput}
                    readOnly
                    autoSize={{ minRows: 10, maxRows: 22 }}
                    style={{ marginTop: 8, fontFamily: 'ui-monospace, Consolas, monospace', fontSize: 12, whiteSpace: 'pre' }}
                  />
                ) : null}
              </Form>
            ) : (
              <div className="connector-tool-empty">
                <Empty
                  description="从左侧选择 tool，或新建一个"
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                >
                  <Button type="primary" size="small" onClick={addTool}>
                    + 新建 Tool
                  </Button>
                </Empty>
              </div>
            )}
          </div>
        </div>
      </Modal>
    </>
  )
}
