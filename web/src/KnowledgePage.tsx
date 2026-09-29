import { useEffect, useState } from 'react'
import {
  Avatar,
  Button,
  Card,
  Empty,
  Flex,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd'
import {
  addKnowledgeDocument,
  createKnowledgeBase,
  deleteKnowledgeBase,
  deleteKnowledgeDocument,
  listKnowledgeDocuments,
  updateKnowledgeBase,
  type KnowledgeBase,
  type KnowledgeDocument,
  type Provider,
} from './api'

function statusLabel(status: string) {
  if (status === 'READY') {
    return '已就绪'
  }
  if (status === 'FAILED') {
    return '失败'
  }
  return '处理中'
}

export function KnowledgePage({
  bases,
  providers,
  onChanged,
  onError,
}: {
  bases: KnowledgeBase[]
  providers: Provider[]
  onChanged: () => Promise<void>
  onError: (message: string) => void
}) {
  const embeddingProviders = providers.filter((item) => item.type === 'EMBEDDING')
  const [view, setView] = useState<'list' | 'create' | string>('list')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [embeddingProviderId, setEmbeddingProviderId] = useState('')
  const [topK, setTopK] = useState(4)
  const [chunkSize, setChunkSize] = useState(800)
  const [chunkOverlap, setChunkOverlap] = useState(80)
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([])
  const [docName, setDocName] = useState('')
  const [docContent, setDocContent] = useState('')
  const [saving, setSaving] = useState(false)
  const [indexing, setIndexing] = useState(false)
  const editingId = view !== 'list' && view !== 'create' ? view : null
  const current = bases.find((item) => item.id === editingId)

  useEffect(() => {
    if (!embeddingProviderId && embeddingProviders[0]) {
      setEmbeddingProviderId(embeddingProviders[0].id)
    }
  }, [embeddingProviderId, embeddingProviders])

  useEffect(() => {
    if (!editingId) {
      setDocuments([])
      return
    }
    void listKnowledgeDocuments(editingId)
      .then(setDocuments)
      .catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
  }, [editingId, onError])

  function fill(item: KnowledgeBase) {
    setName(item.name)
    setDescription(item.description ?? '')
    setEmbeddingProviderId(item.embeddingProviderId)
    setTopK(item.topK ?? 4)
    setChunkSize(item.chunkSize ?? 800)
    setChunkOverlap(item.chunkOverlap ?? 80)
  }

  function resetForm() {
    setName('')
    setDescription('')
    setTopK(4)
    setChunkSize(800)
    setChunkOverlap(80)
    setDocName('')
    setDocContent('')
    if (embeddingProviders[0]) {
      setEmbeddingProviderId(embeddingProviders[0].id)
    }
  }

  function openCreate() {
    resetForm()
    setView('create')
  }

  function openBase(item: KnowledgeBase) {
    fill(item)
    setView(item.id)
  }

  function backToList() {
    resetForm()
    setView('list')
  }

  function body() {
    return {
      name: name.trim(),
      description: description.trim(),
      embeddingProviderId,
      topK,
      chunkSize,
      chunkOverlap,
    }
  }

  async function save() {
    if (!name.trim()) {
      onError('请填写知识库名称')
      return
    }
    if (!embeddingProviderId) {
      onError('请先在模型提供商里新增一个 Embedding 模型')
      return
    }
    setSaving(true)
    try {
      if (editingId) {
        await updateKnowledgeBase(editingId, body())
        await onChanged()
      } else {
        const created = await createKnowledgeBase(body())
        await onChanged()
        setView(created.id)
      }
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  async function refreshDocuments(id: string) {
    setDocuments(await listKnowledgeDocuments(id))
    await onChanged()
  }

  async function submitDocument(title: string, content: string) {
    if (!editingId) {
      return
    }
    if (!content.trim()) {
      onError('正文不能为空')
      return
    }
    setIndexing(true)
    try {
      await addKnowledgeDocument(editingId, title.trim() || '未命名文档', content)
      setDocName('')
      setDocContent('')
      await refreshDocuments(editingId)
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
      await refreshDocuments(editingId).catch(() => undefined)
    } finally {
      setIndexing(false)
    }
  }

  if (view === 'list') {
    return (
      <div>
        <Flex justify="space-between" align="flex-start" style={{ marginBottom: 20 }}>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              知识库
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0' }}>
              上传文档后按 Embedding 模型切块保存。智能体绑上知识库后，对话会先检索相关片段。
            </Typography.Paragraph>
          </div>
          <Button type="primary" onClick={openCreate}>
            新建知识库
          </Button>
        </Flex>
        {bases.length === 0 ? (
          <Card>
            <Empty description="还没有知识库。先配置 Embedding 模型，再新建并上传文档。" />
          </Card>
        ) : (
          <div className="card-grid">
            {bases.map((item) => (
              <Card key={item.id} hoverable onClick={() => openBase(item)}>
                <Space vertical size={8} style={{ width: '100%' }}>
                  <Avatar style={{ background: '#722ed1' }}>{item.name.slice(0, 1)}</Avatar>
                  <Typography.Title level={5} style={{ margin: 0 }}>
                    {item.name}
                  </Typography.Title>
                  <Typography.Text type="secondary">{item.description || '暂无描述'}</Typography.Text>
                  <Typography.Text type="secondary">
                    {item.embeddingProviderName} · {item.embeddingModel || '未填模型'}
                  </Typography.Text>
                  <Space wrap>
                    <Tag>{item.documentCount} 篇文档</Tag>
                    {item.embeddingDim ? <Tag>{item.embeddingDim} 维</Tag> : null}
                    <Tag>检索 {item.topK} 条</Tag>
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
            {editingId ? name.trim() || '编辑知识库' : '新建知识库'}
          </Typography.Title>
        </Space>
        {editingId ? (
          <Popconfirm
            title="删除该知识库？文档和向量会一起删掉。"
            onConfirm={() => {
              void deleteKnowledgeBase(editingId)
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
      <Card title="基本信息">
        <Form layout="vertical" onFinish={() => void save()}>
          <Form.Item label="名称">
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="例如 产品手册" required />
          </Form.Item>
          <Form.Item label="描述">
            <Input.TextArea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={3}
              placeholder="这个库放什么资料"
            />
          </Form.Item>
          <Form.Item
            label="Embedding 模型"
            extra="在「模型提供商」里把类型选成 Embedding 模型后，这里才能选到。"
          >
            {embeddingProviders.length === 0 ? (
              <Empty description="还没有 Embedding 模型" />
            ) : (
              <Select
                value={embeddingProviderId || undefined}
                onChange={setEmbeddingProviderId}
                options={embeddingProviders.map((item) => ({
                  value: item.id,
                  label: `${item.name} · ${item.defaultModel || '未填模型'}`,
                }))}
              />
            )}
          </Form.Item>
          <Space size={16} wrap>
            <Form.Item label="检索条数">
              <InputNumber min={1} max={10} value={topK} onChange={(value) => setTopK(value ?? 4)} />
            </Form.Item>
            <Form.Item label="分块长度">
              <InputNumber min={200} max={2000} value={chunkSize} onChange={(value) => setChunkSize(value ?? 800)} />
            </Form.Item>
            <Form.Item label="重叠长度">
              <InputNumber
                min={0}
                max={Math.floor(chunkSize / 2)}
                value={chunkOverlap}
                onChange={(value) => setChunkOverlap(value ?? 80)}
              />
            </Form.Item>
          </Space>
          <Button type="primary" htmlType="submit" loading={saving}>
            {editingId ? '保存' : '创建并继续上传'}
          </Button>
        </Form>
      </Card>
      {editingId ? (
        <Card title="文档" style={{ marginTop: 16 }}>
          <Typography.Paragraph type="secondary">
            支持 txt、md。上传后会立刻调用 Embedding 模型切块并保存向量。
            {current?.embeddingDim ? ` 当前向量维度 ${current.embeddingDim}。` : ''}
          </Typography.Paragraph>
          <Space wrap style={{ marginBottom: 16 }}>
            <Upload
              accept=".txt,.md,.markdown"
              showUploadList={false}
              beforeUpload={(file) => {
                const reader = new FileReader()
                reader.onload = () => {
                  void submitDocument(file.name, String(reader.result ?? ''))
                }
                reader.onerror = () => onError('读取文件失败')
                reader.readAsText(file)
                return false
              }}
            >
              <Button loading={indexing}>上传文本</Button>
            </Upload>
          </Space>
          <Form layout="vertical" onFinish={() => void submitDocument(docName, docContent)}>
            <Form.Item label="文档名">
              <Input value={docName} onChange={(e) => setDocName(e.target.value)} placeholder="例如 退款说明.md" />
            </Form.Item>
            <Form.Item label="正文">
              <Input.TextArea
                value={docContent}
                onChange={(e) => setDocContent(e.target.value)}
                rows={8}
                placeholder="把资料粘贴到这里"
              />
            </Form.Item>
            <Button htmlType="submit" loading={indexing}>
              入库
            </Button>
          </Form>
          <Table
            style={{ marginTop: 16 }}
            rowKey="id"
            pagination={false}
            dataSource={documents}
            locale={{ emptyText: '还没有文档' }}
            columns={[
              { title: '名称', dataIndex: 'name' },
              {
                title: '状态',
                dataIndex: 'status',
                render: (status: string, row: KnowledgeDocument) => (
                  <Space vertical size={0}>
                    <Tag color={status === 'READY' ? 'green' : status === 'FAILED' ? 'red' : 'blue'}>
                      {statusLabel(status)}
                    </Tag>
                    {row.errorMessage ? (
                      <Typography.Text type="danger" style={{ fontSize: 12 }}>
                        {row.errorMessage}
                      </Typography.Text>
                    ) : null}
                  </Space>
                ),
              },
              { title: '字数', dataIndex: 'charCount', width: 90 },
              { title: '分块', dataIndex: 'chunkCount', width: 80 },
              {
                title: '',
                width: 80,
                render: (_value, row: KnowledgeDocument) => (
                  <Button
                    type="link"
                    danger
                    onClick={() => {
                      if (!editingId) {
                        return
                      }
                      void deleteKnowledgeDocument(editingId, row.id)
                        .then(() => refreshDocuments(editingId))
                        .catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
                    }}
                  >
                    删除
                  </Button>
                ),
              },
            ]}
          />
        </Card>
      ) : null}
    </div>
  )
}
