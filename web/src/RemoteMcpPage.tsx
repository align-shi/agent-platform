import { useState } from 'react'
import {
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd'
import {
  createRemoteMcp,
  deleteRemoteMcp,
  refreshRemoteMcp,
  tryRemoteMcp,
  updateRemoteMcp,
  type HttpAuthType,
  type McpTransport,
  type RemoteMcp,
} from './api'

const SAVED_SECRET_MASK = '••••••••••••••••'

function authLabel(type: HttpAuthType) {
  if (type === 'BEARER') {
    return 'Bearer'
  }
  if (type === 'HEADER') {
    return '自定义 Header'
  }
  return '无鉴权'
}

function transportLabel(type: McpTransport) {
  return type === 'SSE' ? 'SSE' : 'Streamable HTTP'
}

export function RemoteMcpSection({
  servers,
  onChanged,
  onError,
}: {
  servers: RemoteMcp[]
  onChanged: () => Promise<void>
  onError: (message: string) => void
}) {
  const [open, setOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [url, setUrl] = useState('https://')
  const [transport, setTransport] = useState<McpTransport>('STREAMABLE')
  const [authType, setAuthType] = useState<HttpAuthType>('NONE')
  const [authHeader, setAuthHeader] = useState('Authorization')
  const [secret, setSecret] = useState('')
  const [enabled, setEnabled] = useState(true)
  const [timeoutMs, setTimeoutMs] = useState(15000)
  const [tools, setTools] = useState<RemoteMcp['tools']>([])
  const [lastError, setLastError] = useState('')
  const [serverInfo, setServerInfo] = useState('')
  const [saving, setSaving] = useState(false)
  const [trying, setTrying] = useState(false)
  const [refreshing, setRefreshing] = useState(false)
  const [tryOutput, setTryOutput] = useState('')
  const editing = servers.find((item) => item.id === editingId)

  function liveSecret() {
    const value = secret.trim()
    if (!value || value === SAVED_SECRET_MASK) {
      return undefined
    }
    return value
  }

  function fill(item: RemoteMcp) {
    setEditingId(item.id)
    setName(item.name)
    setDescription(item.description ?? '')
    setUrl(item.url)
    setTransport(item.transport === 'SSE' ? 'SSE' : 'STREAMABLE')
    setAuthType(item.authType)
    setAuthHeader(item.authHeader || 'Authorization')
    setSecret(item.secretConfigured ? SAVED_SECRET_MASK : '')
    setEnabled(item.enabled)
    setTimeoutMs(item.timeoutMs || 15000)
    setTools(item.tools ?? [])
    setLastError(item.lastError ?? '')
    setServerInfo([item.serverName, item.serverVersion].filter(Boolean).join(' '))
    setTryOutput('')
  }

  function resetForm() {
    setEditingId(null)
    setName('')
    setDescription('')
    setUrl('https://')
    setTransport('STREAMABLE')
    setAuthType('NONE')
    setAuthHeader('Authorization')
    setSecret('')
    setEnabled(true)
    setTimeoutMs(15000)
    setTools([])
    setLastError('')
    setServerInfo('')
    setTryOutput('')
  }

  function openCreate() {
    resetForm()
    setOpen(true)
  }

  function openEdit(item: RemoteMcp) {
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

  function body() {
    return {
      name: name.trim(),
      description,
      url: url.trim(),
      transport,
      authType,
      authHeader,
      timeoutMs,
      enabled,
      ...(liveSecret() ? { secret: liveSecret() } : {}),
    }
  }

  async function onSubmit() {
    if (!name.trim()) {
      onError('请填写显示名')
      return
    }
    if (!url.trim() || url.trim() === 'https://') {
      onError('请填写 MCP 服务地址')
      return
    }
    if (authType !== 'NONE') {
      const hasNewSecret = Boolean(liveSecret())
      if (!editingId && !hasNewSecret) {
        onError('该鉴权方式需要填写密钥')
        return
      }
      if (editingId && !editing?.secretConfigured && !hasNewSecret) {
        onError('该鉴权方式需要填写密钥')
        return
      }
    }
    setSaving(true)
    try {
      const saved = editingId ? await updateRemoteMcp(editingId, body()) : await createRemoteMcp(body())
      message.success(editingId ? '远程连接器已保存' : '远程连接器已创建')
      if (saved.lastError) {
        onError(saved.lastError)
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

  async function onRefresh() {
    if (!editingId) {
      onError('请先保存，再刷新工具列表')
      return
    }
    setRefreshing(true)
    try {
      const saved = await refreshRemoteMcp(editingId, liveSecret())
      fill(saved)
      message.success(`已发现 ${saved.tools?.length ?? 0} 个工具`)
      await onChanged()
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setRefreshing(false)
    }
  }

  async function onTry() {
    if (!editingId) {
      onError('请先保存，再试连接')
      return
    }
    setTrying(true)
    try {
      setTryOutput(await tryRemoteMcp(editingId, liveSecret()))
      await onChanged()
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
            远程连接器
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
            填你自己的 MCP 服务地址（Streamable HTTP 或 SSE）。Hub 当客户端去 initialize / tools/list / tools/call，不托管对方进程。
          </Typography.Paragraph>
        </div>
        <Button type="primary" onClick={openCreate}>
          创建
        </Button>
      </Flex>
      {servers.length === 0 ? (
        <Card>
          <Empty description="还没有远程连接器。需要你先有一个可访问的 MCP 端点，再把 URL 登记进来。" />
        </Card>
      ) : (
        <div className="card-grid">
          {servers.map((item) => (
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
                  title="删除这个远程连接器？"
                  onConfirm={(event) => {
                    event?.stopPropagation()
                    void deleteRemoteMcp(item.id)
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
                <Tag color={item.enabled ? 'purple' : 'default'}>{transportLabel(item.transport)}</Tag>
                <Typography.Title level={5} style={{ margin: 0 }}>
                  {item.name}
                  {item.enabled ? '' : '（停用）'}
                </Typography.Title>
                <Typography.Text type="secondary" className="http-card-url">
                  {item.url}
                </Typography.Text>
                <Space wrap>
                  <Tag>{authLabel(item.authType)}</Tag>
                  <Tag>{`${item.tools?.length ?? 0} 个工具`}</Tag>
                  {item.serverName ? <Tag>{item.serverName}</Tag> : null}
                </Space>
                {item.lastError ? (
                  <Typography.Text type="danger" style={{ fontSize: 12 }}>
                    {item.lastError}
                  </Typography.Text>
                ) : null}
              </Space>
            </Card>
          ))}
        </div>
      )}
      <Modal
        className="connector-modal"
        title={editingId ? '编辑远程连接器' : '新建远程连接器'}
        open={open}
        mask={{ closable: false }}
        keyboard={false}
        onCancel={closeDialog}
        width={720}
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
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="例如：公司内部 MCP" />
          </Form.Item>
          <Form.Item label="描述" style={{ marginBottom: 8 }}>
            <Input.TextArea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="这组远程工具做什么"
              rows={3}
            />
          </Form.Item>
          <Form.Item label="MCP 地址" required style={{ marginBottom: 8 }}>
            <Input
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://your-host/mcp 或 SSE 地址"
            />
          </Form.Item>
          <Flex gap={12} wrap>
            <Form.Item label="传输" style={{ width: 200, marginBottom: 8 }}>
              <Select
                value={transport}
                onChange={(value) => setTransport(value)}
                options={[
                  { value: 'STREAMABLE', label: 'Streamable HTTP' },
                  { value: 'SSE', label: 'SSE' },
                ]}
              />
            </Form.Item>
            <Form.Item label="超时(ms)" style={{ width: 160, marginBottom: 8 }}>
              <InputNumber min={1000} max={60000} value={timeoutMs} onChange={(value) => setTimeoutMs(value || 15000)} />
            </Form.Item>
            <Form.Item label="启用" style={{ marginBottom: 8 }}>
              <Switch size="small" checked={enabled} onChange={setEnabled} />
            </Form.Item>
          </Flex>
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
          </Flex>
        </Form>
        <Flex justify="space-between" align="center" style={{ margin: '4px 0 8px' }}>
          <Typography.Text strong>已发现的 Tools</Typography.Text>
          <Space>
            <Button size="small" disabled={!editingId} loading={refreshing} onClick={() => void onRefresh()}>
              刷新工具
            </Button>
            <Button size="small" disabled={!editingId} loading={trying} onClick={() => void onTry()}>
              试连接
            </Button>
          </Space>
        </Flex>
        {serverInfo ? (
          <Typography.Paragraph type="secondary" style={{ margin: '0 0 8px' }}>
            对端：{serverInfo}
            {lastError ? ` · ${lastError}` : ''}
          </Typography.Paragraph>
        ) : (
          <Typography.Paragraph type="secondary" style={{ margin: '0 0 8px' }}>
            {editingId ? '保存后会自动尝试发现工具。连不上也可以先保存地址。' : '先保存，再试连接或刷新工具列表。'}
          </Typography.Paragraph>
        )}
        {tools.length === 0 ? (
          <Empty description="还没有从对端发现工具" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Space wrap>
            {tools.map((item) => (
              <Tag key={item.name} title={item.description}>
                {item.name}
              </Tag>
            ))}
          </Space>
        )}
        {tryOutput ? (
          <Input.TextArea
            value={tryOutput}
            readOnly
            autoSize={{ minRows: 8, maxRows: 18 }}
            style={{ marginTop: 12, fontFamily: 'ui-monospace, Consolas, monospace', fontSize: 12, whiteSpace: 'pre' }}
          />
        ) : null}
      </Modal>
    </>
  )
}
