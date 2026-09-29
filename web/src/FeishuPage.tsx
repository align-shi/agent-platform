import { useEffect, useState } from 'react'
import { Alert, Button, Card, Empty, Form, Input, Modal, Popconfirm, Select, Space, Switch, Tag, Typography, message } from 'antd'
import {
  createFeishuBot,
  deleteFeishuBot,
  listFeishuBots,
  probeFeishuBot,
  updateFeishuBot,
  type Agent,
  type FeishuBot,
  type UpsertFeishuBot,
} from './api'

type FormValues = {
  name: string
  agentId: string
  appId: string
  appSecret?: string
  verificationToken?: string
  encryptEnabled: boolean
  encryptKey?: string
  publicBaseUrl?: string
  enabled: boolean
}

export function FeishuPage({
  agents,
  onError,
}: {
  agents: Agent[]
  onError: (message: string) => void
}) {
  const [bots, setBots] = useState<FeishuBot[]>([])
  const [loaded, setLoaded] = useState(false)
  const [open, setOpen] = useState(false)
  const [editing, setEditing] = useState<FeishuBot | null>(null)
  const [saving, setSaving] = useState(false)
  const [probingId, setProbingId] = useState<string | null>(null)
  const [form] = Form.useForm<FormValues>()
  const encryptEnabled = Form.useWatch('encryptEnabled', form)

  async function refresh() {
    const next = await listFeishuBots()
    setBots(next)
    setLoaded(true)
  }

  useEffect(() => {
    void refresh().catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
  }, [onError])

  function openCreate() {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      name: '飞书机器人',
      agentId: agents[0]?.id,
      appId: '',
      appSecret: '',
      verificationToken: '',
      encryptEnabled: false,
      encryptKey: '',
      publicBaseUrl: '',
      enabled: true,
    })
    setOpen(true)
  }

  function openEdit(bot: FeishuBot) {
    setEditing(bot)
    form.resetFields()
    form.setFieldsValue({
      name: bot.name,
      agentId: bot.agentId,
      appId: bot.appId,
      appSecret: '',
      verificationToken: '',
      encryptEnabled: bot.encryptConfigured,
      encryptKey: '',
      publicBaseUrl: bot.publicBaseUrl,
      enabled: bot.enabled,
    })
    setOpen(true)
  }

  async function onSubmit(values: FormValues) {
    const body: UpsertFeishuBot = {
      name: values.name,
      appId: values.appId,
      appSecret: values.appSecret,
      verificationToken: values.verificationToken,
      encryptKey: values.encryptKey,
      encryptEnabled: Boolean(values.encryptEnabled),
      agentId: values.agentId,
      enabled: Boolean(values.enabled),
      publicBaseUrl: values.publicBaseUrl,
    }
    setSaving(true)
    try {
      if (editing) {
        await updateFeishuBot(editing.id, body)
        message.success('飞书机器人已保存')
      } else {
        await createFeishuBot(body)
        message.success('已添加。把回调地址填到飞书的事件订阅里')
      }
      setOpen(false)
      await refresh()
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  async function onProbe(bot: FeishuBot) {
    setProbingId(bot.id)
    try {
      await probeFeishuBot(bot.id)
      message.success('飞书凭证可用')
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setProbingId(null)
    }
  }

  async function onDelete(bot: FeishuBot) {
    try {
      await deleteFeishuBot(bot.id)
      message.success('已删除')
      await refresh()
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  return (
    <div>
      <Typography.Title level={3} style={{ margin: 0 }}>
        飞书
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ margin: '8px 0 16px' }}>
        飞书用户给机器人发消息，Hub 用绑定的智能体回复，同一个会话会接着聊。
      </Typography.Paragraph>

      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="飞书要能访问到 Hub"
        description="飞书只会请求公网 HTTPS。本机开发时，把 Hub 的 8080 映射出去，让回调打到 Hub，而不是只打开前端页面。"
      />

      <Card title="怎么接上" style={{ marginBottom: 16 }}>
        <ol style={{ margin: 0, paddingLeft: 20, lineHeight: 1.8 }}>
          <li>打开飞书开放平台，创建企业自建应用，在应用能力里打开机器人。</li>
          <li>
            开通权限：获取用户发给机器人的单聊消息（im:message.p2p_msg:readonly）、接收群聊中 @机器人
            消息事件（im:message.group_at_msg:readonly）、以应用的身份发消息（im:message:send_as_bot）。
          </li>
          <li>把 App ID、App Secret、Verification Token 填到下面，并选一个智能体。Encrypt Key 两边要么都空，要么填同一串。</li>
          <li>
            事件订阅选择「将事件发送至开发者服务器」，订阅接收消息 im.message.receive_v1，请求地址填机器人卡片上的回调地址。
          </li>
          <li>发布应用版本。在飞书里找到这个机器人，直接发文字。</li>
        </ol>
      </Card>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Typography.Title level={5} style={{ margin: 0 }}>
          机器人
        </Typography.Title>
        <Button type="primary" onClick={openCreate} disabled={agents.length === 0}>
          添加机器人
        </Button>
      </div>
      {agents.length === 0 ? (
        <Typography.Paragraph type="secondary">先在「智能体」里创建一个智能体，再回来绑定。</Typography.Paragraph>
      ) : null}

      {!loaded ? null : bots.length === 0 ? (
        <Empty description="还没有飞书机器人" />
      ) : (
        <div className="card-grid">
          {bots.map((bot) => {
            const callback = bot.callbackUrl || bot.callbackPath
            return (
              <Card key={bot.id}>
                <Space vertical size={8} style={{ width: '100%' }}>
                  <Space>
                    <Typography.Title level={5} style={{ margin: 0 }}>
                      {bot.name}
                    </Typography.Title>
                    {bot.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>}
                  </Space>
                  <Typography.Text type="secondary">智能体 {bot.agentName || '未绑定'}</Typography.Text>
                  <Typography.Text type="secondary">App ID {bot.appId}</Typography.Text>
                  <div>
                    <Typography.Text type="secondary">{bot.callbackUrl ? '回调地址' : '回调路径'}</Typography.Text>
                    <Typography.Paragraph copyable={{ text: callback }} style={{ margin: '4px 0 0', wordBreak: 'break-all' }}>
                      {callback}
                    </Typography.Paragraph>
                    {bot.callbackUrl ? null : (
                      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                        编辑时填上公网 https 根地址后，这里会变成飞书要的完整地址。
                      </Typography.Text>
                    )}
                  </div>
                  <Typography.Text type="secondary">
                    {bot.lastEventAt ? `最近事件 ${new Date(bot.lastEventAt).toLocaleString()}` : '还没有收到飞书事件'}
                  </Typography.Text>
                  {bot.lastError ? (
                    <Typography.Paragraph type="danger" style={{ margin: 0, wordBreak: 'break-all' }}>
                      {bot.lastError}
                    </Typography.Paragraph>
                  ) : null}
                  <Space>
                    <Button onClick={() => openEdit(bot)}>编辑</Button>
                    <Button loading={probingId === bot.id} onClick={() => void onProbe(bot)}>
                      测试凭证
                    </Button>
                    <Popconfirm title="删除这个机器人？" okText="删除" cancelText="取消" onConfirm={() => void onDelete(bot)}>
                      <Button danger>删除</Button>
                    </Popconfirm>
                  </Space>
                </Space>
              </Card>
            )
          })}
        </div>
      )}

      <Modal
        title={editing ? '编辑飞书机器人' : '添加飞书机器人'}
        open={open}
        confirmLoading={saving}
        okText="保存"
        cancelText="取消"
        onOk={() => form.submit()}
        onCancel={() => setOpen(false)}
        forceRender
      >
        <Form form={form} layout="vertical" onFinish={(values) => void onSubmit(values)} style={{ marginTop: 16 }}>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请填写名称' }]}>
            <Input maxLength={80} />
          </Form.Item>
          <Form.Item name="agentId" label="智能体" rules={[{ required: true, message: '请选择智能体' }]}>
            <Select
              options={agents.map((agent) => ({ value: agent.id, label: agent.name }))}
              placeholder="选择智能体"
            />
          </Form.Item>
          <Form.Item name="appId" label="App ID" rules={[{ required: true, message: '请填写 App ID' }]}>
            <Input placeholder="cli_xxx" autoComplete="off" />
          </Form.Item>
          <Form.Item
            name="appSecret"
            label="App Secret"
            rules={editing ? [] : [{ required: true, message: '请填写 App Secret' }]}
            extra={editing ? `已保存末四位 ${editing.secretLast4 || '----'}，留空则不修改` : undefined}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="verificationToken"
            label="Verification Token"
            rules={editing ? [] : [{ required: true, message: '请填写 Verification Token' }]}
            extra={editing ? `已保存末四位 ${editing.tokenLast4 || '----'}，留空则不修改` : '飞书事件订阅页里的 Verification Token'}
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="encryptEnabled" label="事件加密" valuePropName="checked">
            <Switch />
          </Form.Item>
          {encryptEnabled ? (
            <Form.Item
              name="encryptKey"
              label="Encrypt Key"
              rules={editing?.encryptConfigured ? [] : [{ required: true, message: '请填写 Encrypt Key' }]}
              extra="与飞书后台的 Encrypt Key 保持一致。留空表示不修改。"
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          ) : null}
          <Form.Item name="publicBaseUrl" label="公网根地址" extra="只填 https 域名，例如 https://xxx.example.com，不要带路径。">
            <Input placeholder="https://" autoComplete="off" />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
