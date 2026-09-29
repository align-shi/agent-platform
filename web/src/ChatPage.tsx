import { useEffect, useState } from 'react'
import { Button, Empty, Input, Popconfirm, Select, Typography } from 'antd'
import {
  deleteConversation,
  listConversations,
  listMessages,
  streamChat,
  type Agent,
  type ChatMessage,
  type Conversation,
  type HttpConnector,
  type MemoryMode,
  type RemoteMcp,
  type Skill,
} from './api'

function memoryModeLabel(mode: MemoryMode | undefined) {
  if (mode === 'CROSS') {
    return '跨会话记忆'
  }
  if (mode === 'NONE') {
    return '无记忆'
  }
  return '当前会话记忆'
}

export function ChatPage({
  agents,
  skills,
  httpTools,
  remoteMcps,
  onError,
}: {
  agents: Agent[]
  skills: Skill[]
  httpTools: HttpConnector[]
  remoteMcps: RemoteMcp[]
  onError: (message: string) => void
}) {
  const [agentId, setAgentId] = useState('')
  const [conversationId, setConversationId] = useState('')
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('用一句话介绍你自己')
  const [pending, setPending] = useState(false)
  const [status, setStatus] = useState('')
  const current = agents.find((item) => item.id === agentId)

  useEffect(() => {
    if (!agentId && agents[0]) {
      setAgentId(agents[0].id)
    }
  }, [agents, agentId])

  useEffect(() => {
    if (!agentId) {
      setConversations([])
      setConversationId('')
      setMessages([])
      return
    }
    void listConversations(agentId)
      .then((items) => {
        setConversations(items)
        setConversationId('')
        setMessages([])
      })
      .catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
  }, [agentId, onError])

  async function loadConversation(id: string) {
    setConversationId(id)
    if (!id) {
      setMessages([])
      return
    }
    try {
      setMessages(await listMessages(id))
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function send() {
    if (!agentId || !input.trim() || pending) {
      return
    }
    const content = input.trim()
    const next: ChatMessage[] = [...messages, { role: 'user', content }]
    setMessages(next)
    setInput('')
    setPending(true)
    setStatus('')
    let assistant = ''
    let citations: ChatMessage['citations'] = []
    setMessages([...next, { role: 'assistant', content: '' }])
    try {
      await streamChat(
        { agentId, conversationId: conversationId || undefined, content },
        (delta) => {
          assistant += delta
          setStatus('')
          setMessages([...next, { role: 'assistant', content: assistant, citations }])
        },
        (id) => {
          setConversationId(id)
        },
        (text) => {
          setStatus(text)
        },
        (items) => {
          citations = items
          setMessages([...next, { role: 'assistant', content: assistant, citations }])
        },
      )
      setConversations(await listConversations(agentId))
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setPending(false)
      setStatus('')
    }
  }

  const boundNames = current
    ? [
        ...(current.skillIds ?? []).map((id) => skills.find((item) => item.id === id)?.name ?? id),
        ...(current.httpToolIds ?? []).map((id) => httpTools.find((item) => item.id === id)?.name ?? id),
        ...(current.mcpServerIds ?? []).map((id) => remoteMcps.find((item) => item.id === id)?.name ?? id),
      ]
    : []

  return (
    <section className="chat-page">
      <aside className="chat-side">
        <div className="chat-side-head">
          <Typography.Text type="secondary">智能体</Typography.Text>
          <Select
            style={{ width: '100%', marginTop: 8 }}
            value={agentId || undefined}
            placeholder="请先创建智能体"
            onChange={setAgentId}
            options={agents.map((item) => ({ value: item.id, label: item.name }))}
          />
          {current ? (
            <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0', fontSize: 12 }}>
              {current.providerName} / {current.model} · {memoryModeLabel(current.memoryMode)}
              {boundNames.length ? ` · ${boundNames.join('、')}` : ''}
            </Typography.Paragraph>
          ) : null}
        </div>
        <div className="chat-history-head">
          <Typography.Text type="secondary">对话历史</Typography.Text>
          <Button
            size="small"
            disabled={!agentId || pending}
            onClick={() => {
              setConversationId('')
              setMessages([])
            }}
          >
            新对话
          </Button>
        </div>
        <div className="chat-history">
          {conversations.length === 0 ? (
            <Typography.Paragraph type="secondary" style={{ margin: 0, fontSize: 13 }}>
              还没有对话
            </Typography.Paragraph>
          ) : (
            conversations.map((item) => (
              <div key={item.id} className={item.id === conversationId ? 'chat-history-item active' : 'chat-history-item'}>
                <button type="button" disabled={pending} onClick={() => void loadConversation(item.id)}>
                  {item.title || '未命名对话'}
                </button>
                <Popconfirm
                  title="删除这个会话？"
                  disabled={pending}
                  onConfirm={() => {
                    void deleteConversation(item.id)
                      .then(async () => {
                        if (conversationId === item.id) {
                          setConversationId('')
                          setMessages([])
                        }
                        setConversations(await listConversations(agentId))
                      })
                      .catch((err: unknown) => onError(err instanceof Error ? err.message : String(err)))
                  }}
                >
                  <Button type="text" size="small" danger disabled={pending}>
                    删除
                  </Button>
                </Popconfirm>
              </div>
            ))
          )}
        </div>
      </aside>
      <div className="chat-main">
        <div className="transcript">
          {messages.length === 0 ? (
            <Empty description="对这个智能体发一条消息。绑定技能后，模型会先看目录再按需读取文件。" />
          ) : null}
          {status ? (
            <Typography.Text type="secondary" className="status-line">
              {status}
            </Typography.Text>
          ) : null}
          {messages.map((item, index) => (
            <article key={`${item.role}-${index}`} className={item.role}>
              <small>{item.role === 'user' ? '你' : '助手'}</small>
              <pre>{item.content}</pre>
              {item.role === 'assistant' && item.citations && item.citations.length > 0 ? (
                <div className="citations">
                  {item.citations.map((cite, citeIndex) => (
                    <div key={`${cite.document}-${citeIndex}`} className="citation">
                      <strong>
                        {cite.knowledgeBase} / {cite.document}
                      </strong>
                      <span>{cite.content}</span>
                    </div>
                  ))}
                </div>
              ) : null}
            </article>
          ))}
        </div>
        <form
          className="composer"
          onSubmit={(event) => {
            event.preventDefault()
            void send()
          }}
        >
          <Input
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="输入消息"
            disabled={!agentId || pending}
          />
          <Button type="primary" htmlType="submit" loading={pending} disabled={!agentId}>
            发送
          </Button>
        </form>
      </div>
    </section>
  )
}
