import { useEffect, useRef, useState } from 'react'
import { Avatar, Button, Card, Empty, Flex, Form, Input, InputNumber, Popconfirm, Select, Space, Tag, Typography } from 'antd'
import {
  createProvider,
  deleteProvider,
  listVendors,
  updateProvider,
  type Provider,
  type ProviderType,
  type VendorPreset,
} from './api'

export function ProvidersPage({
  providers,
  onChanged,
  onError,
}: {
  providers: Provider[]
  onChanged: () => Promise<void>
  onError: (message: string) => void
}) {
  const [vendors, setVendors] = useState<VendorPreset[]>([])
  const [view, setView] = useState<'list' | 'create' | string>('list')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [vendorId, setVendorId] = useState('deepseek')
  const [name, setName] = useState('DeepSeek')
  const [baseUrl, setBaseUrl] = useState('https://api.deepseek.com')
  const [defaultModel, setDefaultModel] = useState('deepseek-chat')
  const [apiKey, setApiKey] = useState('')
  const [type, setType] = useState<ProviderType>('CHAT')
  const [dimensions, setDimensions] = useState<number | null>(null)
  const [saving, setSaving] = useState(false)
  const vendorsReady = useRef(false)
  const selectedVendor = vendors.find((item) => item.id === vendorId)
  const customizable = vendorId === 'custom' || vendorId === 'custom-embed'
  const visibleVendors = vendors.filter((item) => item.type === type)
  const editing = providers.find((item) => item.id === editingId)

  useEffect(() => {
    void listVendors()
      .then((items) => {
        setVendors(items)
        if (!vendorsReady.current) {
          vendorsReady.current = true
          const first = items.find((item) => item.id === 'deepseek') ?? items[0]
          if (first) {
            applyVendor(first)
          }
        }
      })
      .catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
  }, [onError])

  function applyVendor(preset: VendorPreset) {
    setVendorId(preset.id)
    setType(preset.type)
    setDimensions(preset.dimensions ?? null)
    if (preset.id !== 'custom' && preset.id !== 'custom-embed') {
      setName(preset.name)
      setBaseUrl(preset.baseUrl)
      setDefaultModel(preset.defaultModel)
    }
  }

  function fill(item: Provider) {
    setEditingId(item.id)
    setVendorId(item.vendor || (item.type === 'EMBEDDING' ? 'custom-embed' : 'custom'))
    setName(item.name)
    setBaseUrl(item.baseUrl)
    setDefaultModel(item.defaultModel || '')
    setType(item.type)
    setDimensions(item.dimensions ?? null)
    setApiKey('')
  }

  function resetForm() {
    setEditingId(null)
    setApiKey('')
    const first = vendors.find((item) => item.id === 'deepseek') ?? vendors[0]
    if (first) {
      applyVendor(first)
    }
  }

  function openCreate() {
    resetForm()
    setView('create')
  }

  function openProvider(item: Provider) {
    fill(item)
    setView(item.id)
  }

  function backToList() {
    resetForm()
    setView('list')
  }

  async function onSubmit() {
    if (!editingId && !apiKey.trim()) {
      onError('新增时必须填写 API Key')
      return
    }
    setSaving(true)
    try {
      const body = {
        name,
        baseUrl,
        type,
        vendor: vendorId,
        defaultModel,
        dimensions: type === 'EMBEDDING' ? dimensions : null,
        ...(apiKey.trim() ? { apiKey: apiKey.trim() } : {}),
      }
      if (editingId) {
        await updateProvider(editingId, body)
      } else {
        await createProvider(body)
      }
      backToList()
      await onChanged()
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
              模型提供商
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0' }}>
              以卡片查看已配置的模型。点进去可以改名称、地址、模型和密钥。
            </Typography.Paragraph>
          </div>
          <Button type="primary" onClick={openCreate}>
            新增提供商
          </Button>
        </Flex>
        {providers.length === 0 ? (
          <Card>
            <Empty description="还没有提供商，新增后可以给智能体选用。" />
          </Card>
        ) : (
          <div className="card-grid">
            {providers.map((item) => (
              <Card key={item.id} hoverable onClick={() => openProvider(item)}>
                <Space vertical size={8} style={{ width: '100%' }}>
                  <Avatar style={{ background: '#1677ff' }}>{item.name.slice(0, 1)}</Avatar>
                  <Typography.Title level={5} style={{ margin: 0 }}>
                    {item.name}
                  </Typography.Title>
                  <Typography.Text type="secondary">{item.defaultModel || '未填模型'}</Typography.Text>
                  <Typography.Text type="secondary" className="http-card-url">
                    {item.baseUrl}
                  </Typography.Text>
                  <Space wrap>
                    <Tag>{item.vendor ?? 'custom'}</Tag>
                    <Tag color={item.type === 'CHAT' ? 'blue' : 'purple'}>
                      {item.type === 'CHAT' ? '对话' : 'Embedding'}
                    </Tag>
                    {item.type === 'EMBEDDING' && item.dimensions ? <Tag>{item.dimensions} 维</Tag> : null}
                    <Tag>****{item.keyLast4}</Tag>
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
    <div>
      <Flex justify="space-between" align="center" style={{ marginBottom: 20 }} gap={12}>
        <Space>
          <Button onClick={backToList}>返回列表</Button>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {editingId ? name.trim() || '编辑提供商' : '新增提供商'}
          </Typography.Title>
        </Space>
        {editingId ? (
          <Popconfirm
            title="删除该提供商？"
            onConfirm={() => {
              void deleteProvider(editingId)
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
      </Flex>
      <Card title={editingId ? '编辑提供商' : '新增提供商'}>
        <Typography.Paragraph type="secondary">
          {editingId ? '密钥留空表示不更换。' : '新增时必须填写 API Key。'}
        </Typography.Paragraph>
        <Form layout="vertical" onFinish={() => void onSubmit()}>
            <Form.Item label="厂商">
              <Select
                value={vendorId}
                onChange={(value) => {
                  const preset = vendors.find((item) => item.id === value)
                  if (preset) {
                    applyVendor(preset)
                  } else {
                    setVendorId(value)
                  }
                }}
                options={visibleVendors.map((item) => ({ value: item.id, label: item.name }))}
              />
            </Form.Item>
            <Form.Item label="名称">
              <Input value={name} onChange={(e) => setName(e.target.value)} required />
            </Form.Item>
            <Form.Item
              label="Base URL"
              extra={
                type === 'EMBEDDING'
                  ? 'Hub 会请求这个地址下的 /embeddings。千问填到 compatible-mode/v1，智谱填到 /paas/v4。'
                  : vendorId === 'zhipu'
                    ? '智谱实际请求：https://open.bigmodel.cn/api/paas/v4/chat/completions'
                    : 'Hub 会按厂商规则拼接 /chat/completions，不会给智谱误加 /v1。'
              }
            >
              <Input
                value={baseUrl}
                onChange={(e) => setBaseUrl(e.target.value)}
                placeholder={customizable ? 'https://example.com/v1' : selectedVendor?.baseUrl}
                required
              />
            </Form.Item>
            <Form.Item label="默认模型">
              <Input
                value={defaultModel}
                onChange={(e) => setDefaultModel(e.target.value)}
                placeholder="glm-4-flash"
                required
              />
            </Form.Item>
            <Form.Item label="API Key">
              <Input.Password
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder={editingId ? `不填则保留 ****${editing?.keyLast4 ?? ''}` : '必填'}
                required={!editingId}
              />
            </Form.Item>
            <Form.Item label="类型">
              <Select
                value={type}
                onChange={(value) => {
                  const preferred = vendors.find((item) =>
                    value === 'EMBEDDING' ? item.id === 'qwen-embed' : item.id === 'deepseek',
                  )
                  const next = preferred ?? vendors.find((item) => item.type === value)
                  if (next) {
                    applyVendor(next)
                  } else {
                    setType(value)
                  }
                }}
                options={[
                  { value: 'CHAT', label: '对话模型' },
                  { value: 'EMBEDDING', label: 'Embedding 模型' },
                ]}
              />
            </Form.Item>
            {type === 'EMBEDDING' ? (
              <Form.Item label="向量维度" extra="留空则使用厂商默认维度。千问 text-embedding-v4 常用 1024。">
                <InputNumber
                  value={dimensions ?? undefined}
                  min={32}
                  max={4096}
                  style={{ width: 200 }}
                  placeholder="例如 1024"
                  onChange={(value) => setDimensions(typeof value === 'number' ? value : null)}
                />
              </Form.Item>
            ) : null}
            <Button type="primary" htmlType="submit" loading={saving}>
              {editingId ? '更新' : '保存'}
            </Button>
          </Form>
      </Card>
    </div>
  )
}
