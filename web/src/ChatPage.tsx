import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react'
import { Button, Empty, Input, Popconfirm, Typography } from 'antd'
import {
  deleteConversation,
  listConversations,
  listMessages,
  streamChat,
  type ChatMessage,
  type Conversation,
} from './api'

function useAgentChat(agentId: string, onError: (message: string) => void) {
  const [conversationId, setConversationId] = useState('')
  const [conversations, setConversations] = useState<Conversation[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [pending, setPending] = useState(false)
  const [status, setStatus] = useState('')

  useEffect(() => {
    if (!agentId) {
      setConversations([])
      setConversationId('')
      setMessages([])
      return
    }
    let cancelled = false
    void listConversations(agentId)
      .then((items) => {
        if (cancelled) {
          return
        }
        setConversations(items)
        setConversationId('')
        setMessages([])
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          onError(err instanceof Error ? err.message : String(err))
        }
      })
    return () => {
      cancelled = true
    }
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

  function startNew() {
    setConversationId('')
    setMessages([])
  }

  async function removeConversation(id: string) {
    await deleteConversation(id)
    if (conversationId === id) {
      setConversationId('')
      setMessages([])
    }
    setConversations(await listConversations(agentId))
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

  return {
    conversationId,
    conversations,
    messages,
    input,
    setInput,
    pending,
    status,
    loadConversation,
    startNew,
    removeConversation,
    send,
  }
}

function ChatTranscript({
  messages,
  status,
  emptyText,
}: {
  messages: ChatMessage[]
  status: string
  emptyText: string
}) {
  return (
    <>
      {messages.length === 0 ? <Empty description={emptyText} /> : null}
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
    </>
  )
}

export type AgentChatHandle = {
  startNew: () => void
}

function formatConversationTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

export const AgentChatPanel = forwardRef<
  AgentChatHandle,
  { agentId: string; onError: (message: string) => void; onHistoryOpen: (conversationId: string | null) => void }
>(function AgentChatPanel({ agentId, onError, onHistoryOpen }, ref) {
    const chat = useAgentChat(agentId, onError)
    const transcriptRef = useRef<HTMLDivElement>(null)
    const current = chat.conversations.find((item) => item.id === chat.conversationId)

    useImperativeHandle(
      ref,
      () => ({
        startNew: () => {
          chat.startNew()
          onHistoryOpen(null)
        },
      }),
      [chat.startNew, onHistoryOpen],
    )

    useEffect(() => {
      const el = transcriptRef.current
      if (el) {
        el.scrollTop = el.scrollHeight
      }
    }, [chat.messages, chat.status])

    return (
      <div className="agent-chat">
        <aside className="agent-history">
          <div className="agent-history-head">历史对话</div>
          <div className="agent-history-list">
            {chat.conversations.length === 0 ? (
              <Typography.Paragraph type="secondary" style={{ margin: '4px 8px', fontSize: 12 }}>
                还没有对话
              </Typography.Paragraph>
            ) : (
              chat.conversations.map((item) => (
                <div key={item.id} className={item.id === chat.conversationId ? 'agent-history-row active' : 'agent-history-row'}>
                  <button
                    type="button"
                    disabled={chat.pending}
                    onClick={() => {
                      onHistoryOpen(item.id)
                      void chat.loadConversation(item.id)
                    }}
                  >
                    <strong>{item.title || '未命名对话'}</strong>
                    <small>{formatConversationTime(item.updatedAt)}</small>
                  </button>
                  <Popconfirm
                    title="删除这个会话？"
                    disabled={chat.pending}
                    onConfirm={() => {
                      const opened = chat.conversationId === item.id
                      void chat.removeConversation(item.id)
                        .then(() => {
                          if (opened) {
                            onHistoryOpen(null)
                          }
                        })
                        .catch((err: unknown) => {
                          onError(err instanceof Error ? err.message : String(err))
                        })
                    }}
                  >
                    <Button type="text" size="small" danger className="agent-history-delete" disabled={chat.pending}>
                      删除
                    </Button>
                  </Popconfirm>
                </div>
              ))
            )}
          </div>
        </aside>
        <div className="agent-chat-main">
          {current ? <div className="agent-chat-session">{current.title || '未命名对话'}</div> : null}
          <div className="transcript" ref={transcriptRef}>
            <ChatTranscript messages={chat.messages} status={chat.status} emptyText="发一条消息，试用当前已保存的配置。" />
          </div>
          <form
            className="agent-composer"
            onSubmit={(event) => {
              event.preventDefault()
              void chat.send()
            }}
          >
            <Input.TextArea
              variant="borderless"
              value={chat.input}
              autoSize={{ minRows: 2, maxRows: 4 }}
              onChange={(e) => chat.setInput(e.target.value)}
              placeholder="输入消息，Enter 发送"
              disabled={!agentId || chat.pending}
              onPressEnter={(event) => {
                if (event.shiftKey || event.nativeEvent.isComposing) {
                  return
                }
                event.preventDefault()
                void chat.send()
              }}
            />
            <div className="agent-composer-bar">
              <Button type="primary" htmlType="submit" loading={chat.pending} disabled={!agentId}>
                发送
              </Button>
            </div>
          </form>
        </div>
      </div>
    )
  },
)
