import { useEffect, useState } from 'react'
import { Button, Card, Empty, Form, Input, Modal, Popconfirm, Select, Space, Switch, Tag, Typography, message } from 'antd'
import {
  createFeishuBot,
  deleteFeishuBot,
  listFeishuBots,
  lookupFeishuBotName,
  probeFeishuBot,
  updateFeishuBot,
  type Agent,
  type FeishuBot,
  type UpsertFeishuBot,
} from './api'

const SAVED_SECRET_MASK = '********'

function typedSecret(value: string | undefined) {
  const secret = value?.trim() ?? ''
  return secret && secret !== SAVED_SECRET_MASK ? secret : undefined
}

type FormValues = {
  name: string
  agentId: string
  appId: string
  appSecret?: string
  enabled: boolean
}

const LINK_LABEL: Record<FeishuBot['linkStatus'], string> = {
  connected: '长连接已连上',
  connecting: '正在连接',
  reconnecting: '正在重连',
  failed: '长连接失败',
  stopped: '未连接',
}

const LINK_COLOR: Record<FeishuBot['linkStatus'], string | undefined> = {
  connected: 'green',
  connecting: 'processing',
  reconnecting: 'processing',
  failed: 'red',
  stopped: undefined,
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
  const [probing, setProbing] = useState(false)
  const [nameHint, setNameHint] = useState('从飞书读取，不能修改')
  const [form] = Form.useForm<FormValues>()
  const watchedAppId = Form.useWatch('appId', form)
  const watchedSecret = Form.useWatch('appSecret', form)

  async function refresh() {
    const next = await listFeishuBots()
    setBots(next)
    setLoaded(true)
  }

  useEffect(() => {
    void refresh().catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
  }, [onError])

  useEffect(() => {
    const timer = window.setInterval(() => {
      void refresh().catch(() => undefined)
    }, 4000)
    return () => window.clearInterval(timer)
  }, [onError])

  useEffect(() => {
    if (!open) {
      return
    }
    const appId = watchedAppId?.trim() ?? ''
    const secret = typedSecret(watchedSecret)
    if (!appId.startsWith('cli_') || appId.length < 12) {
      if (!editing) {
        form.setFieldValue('name', '')
      }
      return
    }
    if (!secret && !editing) {
      form.setFieldValue('name', '')
      return
    }
    let cancelled = false
    const timer = window.setTimeout(() => {
      setNameHint('正在从飞书读取名称…')
      void lookupFeishuBotName({
        appId,
        appSecret: secret,
        id: secret ? undefined : editing?.id,
      })
        .then((name) => {
          if (cancelled) {
            return
          }
          form.setFieldValue('name', name)
          form.setFields([{ name: 'name', errors: [] }])
          setNameHint('从飞书读取，不能修改')
        })
        .catch((err: unknown) => {
          if (cancelled) {
            return
          }
          form.setFieldValue('name', '')
          const text = err instanceof Error ? err.message : String(err)
          form.setFields([{ name: 'name', errors: [text] }])
          setNameHint('从飞书读取，不能修改')
        })
    }, 500)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [open, watchedAppId, watchedSecret, editing, form])

  function openCreate() {
    setEditing(null)
    form.resetFields()
    form.setFieldsValue({
      name: '',
      agentId: agents[0]?.id,
      appId: '',
      appSecret: '',
      enabled: true,
    })
    setNameHint('从飞书读取，不能修改')
    setOpen(true)
  }

  function openEdit(bot: FeishuBot) {
    setEditing(bot)
    form.resetFields()
    form.setFieldsValue({
      name: bot.name,
      agentId: bot.agentId,
      appId: bot.appId,
      appSecret: bot.secretConfigured ? SAVED_SECRET_MASK : '',
      enabled: bot.enabled,
    })
    setOpen(true)
  }

  async function onSubmit(values: FormValues) {
    const body: UpsertFeishuBot = {
      appId: values.appId,
      appSecret: typedSecret(values.appSecret),
      agentId: values.agentId,
      enabled: Boolean(values.enabled),
    }
    setSaving(true)
    try {
      if (editing) {
        await updateFeishuBot(editing.id, body)
        message.success('飞书机器人已保存')
      } else {
        await createFeishuBot(body)
        message.success('已添加。等长连接连上后，再到飞书里保存订阅方式')
      }
      setOpen(false)
      await refresh()
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  async function onProbe() {
    if (!editing) {
      return
    }
    const appId = String(form.getFieldValue('appId') ?? '').trim()
    const secret = typedSecret(String(form.getFieldValue('appSecret') ?? ''))
    setProbing(true)
    try {
      if (secret) {
        const name = await lookupFeishuBotName({ appId, appSecret: secret })
        form.setFieldValue('name', name)
      } else {
        await probeFeishuBot(editing.id)
      }
      message.success('飞书凭证可用')
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setProbing(false)
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

      <Card title="怎么接上" style={{ marginBottom: 16 }}>
        <ol style={{ margin: 0, paddingLeft: 20, lineHeight: 1.8 }}>
          <li>打开飞书开放平台，创建企业自建应用，在应用能力里打开机器人。</li>
          <li>
            开通权限：获取用户发给机器人的单聊消息（im:message.p2p_msg:readonly）、接收群聊中 @机器人
            消息事件（im:message.group_at_msg:readonly）、以应用的身份发消息（im:message:send_as_bot）、发送和删除消息表情回复（im:message.reactions:write_only）。
          </li>
          <li>把 App ID、App Secret 填到下面，选一个智能体，保存。Hub 会马上发起长连接。</li>
          <li>
            卡片显示「长连接已连上」后，到飞书「事件与回调 → 事件配置」，订阅方式选「使用长连接接收事件」并保存。添加事件
            im.message.receive_v1。
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
          {bots.map((bot) => (
            <Card key={bot.id} style={{ height: '100%' }} styles={{ body: { display: 'flex', flexDirection: 'column', gap: 12, height: '100%' } }}>
              <div>
                <Typography.Title level={5} style={{ margin: 0, lineHeight: 1.4 }}>
                  {bot.name}
                </Typography.Title>
                <Space wrap size={6} style={{ marginTop: 8 }}>
                  {bot.enabled ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>}
                  <Tag color={LINK_COLOR[bot.linkStatus]}>{LINK_LABEL[bot.linkStatus] || bot.linkStatus}</Tag>
                </Space>
              </div>
              <div style={{ display: 'grid', rowGap: 4 }}>
                <Typography.Text type="secondary">智能体 {bot.agentName || '未绑定'}</Typography.Text>
                <Typography.Text type="secondary" ellipsis={{ tooltip: bot.appId }}>
                  App ID {bot.appId}
                </Typography.Text>
                <Typography.Text type="secondary">
                  {bot.lastEventAt ? `最近事件 ${new Date(bot.lastEventAt).toLocaleString()}` : '还没有收到飞书消息'}
                </Typography.Text>
              </div>
              {bot.linkDetail ? (
                <Typography.Paragraph type="danger" style={{ margin: 0, wordBreak: 'break-all' }}>
                  {bot.linkDetail}
                </Typography.Paragraph>
              ) : null}
              {bot.lastError ? (
                <Typography.Paragraph type="danger" style={{ margin: 0, wordBreak: 'break-all' }}>
                  {bot.lastError}
                </Typography.Paragraph>
              ) : null}
              <Space style={{ marginTop: 'auto' }}>
                <Button onClick={() => openEdit(bot)}>编辑</Button>
                <Popconfirm title="删除这个机器人？" okText="删除" cancelText="取消" onConfirm={() => void onDelete(bot)}>
                  <Button danger>删除</Button>
                </Popconfirm>
              </Space>
            </Card>
          ))}
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
          <Form.Item name="name" label="名称" extra={nameHint}>
            <Input disabled placeholder="填写 App ID 和 App Secret 后自动获取" />
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
          >
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          {editing ? (
            <Form.Item>
              <Button loading={probing} onClick={() => void onProbe()}>
                测试凭证
              </Button>
            </Form.Item>
          ) : null}
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
