import { useEffect, useMemo, useState } from 'react'
import { Button, Empty, Spin, Tabs, Tag, Typography } from 'antd'
import { getCall, listCalls, type CallDetail, type CallSpan, type CallSummary } from './api'

const KIND_LABEL: Record<string, string> = {
  system: '系统',
  context: '上下文',
  input: '输入',
  model: '模型',
  tool: '工具',
  assistant: '助手',
  round: '轮次',
}

function formatTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

function formatDuration(ms: number) {
  if (ms < 1000) {
    return `${ms} 毫秒`
  }
  return `${(ms / 1000).toFixed(2)} 秒`
}

function flatten(spans: CallSpan[], depth = 0): Array<{ span: CallSpan; depth: number }> {
  const rows: Array<{ span: CallSpan; depth: number }> = []
  for (const span of spans) {
    if (span.kind !== 'round') {
      rows.push({ span, depth })
    }
    rows.push(...flatten(span.children, span.kind === 'round' ? depth : depth + 1))
  }
  return rows
}

function findSpan(spans: CallSpan[], id: string): CallSpan | null {
  for (const span of spans) {
    if (span.id === id) {
      return span
    }
    const child = findSpan(span.children, id)
    if (child) {
      return child
    }
  }
  return null
}

function firstSpan(spans: CallSpan[]): CallSpan | null {
  for (const span of spans) {
    if (span.kind !== 'round') {
      return span
    }
    const child = firstSpan(span.children)
    if (child) {
      return child
    }
  }
  return spans[0] ?? null
}

function SpanTree({
  spans,
  depth,
  selectedId,
  onSelect,
}: {
  spans: CallSpan[]
  depth: number
  selectedId: string
  onSelect: (id: string) => void
}) {
  return (
    <>
      {spans.map((span) => (
        <div key={span.id}>
          <button
            type="button"
            className={span.id === selectedId ? 'trace-node active' : 'trace-node'}
            style={{ paddingLeft: 8 + depth * 14 }}
            onClick={() => onSelect(span.id)}
          >
            <i data-kind={span.kind} />
            <span>{KIND_LABEL[span.kind] ?? span.kind}</span>
            <strong>{span.title}</strong>
          </button>
          {span.summary ? (
            <div className="trace-node-summary" style={{ paddingLeft: 28 + depth * 14 }}>
              {span.summary}
            </div>
          ) : null}
          {span.children.length > 0 ? (
            <SpanTree spans={span.children} depth={depth + 1} selectedId={selectedId} onSelect={onSelect} />
          ) : null}
        </div>
      ))}
    </>
  )
}

function TraceView({ detail, compact = false }: { detail: CallDetail; compact?: boolean }) {
  const call = detail.call
  const [selectedId, setSelectedId] = useState('')
  const selected = findSpan(detail.spans, selectedId) ?? firstSpan(detail.spans)
  const bars = useMemo(() => flatten(detail.spans), [detail])
  const scale = Math.max(call.latencyMs, 1)

  useEffect(() => {
    setSelectedId(firstSpan(detail.spans)?.id ?? '')
  }, [detail])

  return (
    <div className={compact ? 'trace-view compact' : 'trace-view'}>
      <div className="trace-stats">
        <span>时长 {formatDuration(call.latencyMs)}</span>
        <span>轮次 {call.rounds}</span>
        <span>调用 {call.modelCalls}</span>
        <span>工具 {call.toolCalls}</span>
        <span>MCP {call.mcpCalls}</span>
        <span>
          token {call.promptTokens} / {call.completionTokens} / {call.totalTokens}
        </span>
        <Tag color={call.status === 'SUCCESS' ? 'success' : 'error'}>{call.status === 'SUCCESS' ? '成功' : '失败'}</Tag>
      </div>
      {call.error ? <Typography.Paragraph type="danger">{call.error}</Typography.Paragraph> : null}
      <div className="trace-legend">
        {['system', 'context', 'input', 'model', 'tool', 'assistant'].map((kind) => (
          <span key={kind}>
            <i data-kind={kind} />
            {KIND_LABEL[kind]}
          </span>
        ))}
      </div>
      <div className="trace-waterfall">
        {bars.map(({ span }) => {
          const left = Math.min(98, (span.offsetMs / scale) * 100)
          const width = Math.max(span.durationMs <= 0 ? 0.8 : (span.durationMs / scale) * 100, 0.8)
          return (
            <button
              key={span.id}
              type="button"
              className={selected?.id === span.id ? 'trace-lane active' : 'trace-lane'}
              onClick={() => setSelectedId(span.id)}
            >
              <span>{span.title}</span>
              <span className="trace-track">
                <i data-kind={span.kind} style={{ left: `${left}%`, width: `${Math.min(width, 100 - left)}%` }} />
              </span>
            </button>
          )
        })}
      </div>
      <div className="trace-split">
        <div className="trace-tree">
          <SpanTree spans={detail.spans} depth={0} selectedId={selected?.id ?? ''} onSelect={setSelectedId} />
        </div>
        <div className="trace-detail">
          {selected ? (
            <>
              <div className="trace-detail-head">
                <Typography.Text type="secondary">{KIND_LABEL[selected.kind] ?? selected.kind}</Typography.Text>
                <Typography.Title level={5} style={{ margin: 0 }}>
                  {selected.title}
                </Typography.Title>
              </div>
              {selected.sections.length > 0 ? (
                <Tabs
                  key={selected.id}
                  items={selected.sections.map((section) => ({
                    key: section.key,
                    label: section.label,
                    children: <pre className="trace-pre">{section.text || '空'}</pre>,
                  }))}
                />
              ) : (
                <pre className="trace-pre">{selected.summary || '没有更多内容'}</pre>
              )}
            </>
          ) : (
            <Empty description="没有轨迹" />
          )}
        </div>
      </div>
    </div>
  )
}

export function AgentCallsPanel({
  agentId,
  conversationId,
  onError,
}: {
  agentId: string
  conversationId: string
  onError: (message: string) => void
}) {
  const [calls, setCalls] = useState<CallSummary[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [detail, setDetail] = useState<CallDetail | null>(null)
  const [loadingList, setLoadingList] = useState(false)
  const [loadingDetail, setLoadingDetail] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoadingList(true)
    setSelectedId('')
    setDetail(null)
    listCalls(agentId, conversationId)
      .then((rows) => {
        if (!cancelled) {
          setCalls(rows)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          onError(err instanceof Error ? err.message : String(err))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingList(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [agentId, conversationId, onError])

  useEffect(() => {
    if (!selectedId) {
      setDetail(null)
      return
    }
    let cancelled = false
    setLoadingDetail(true)
    getCall(selectedId)
      .then((next) => {
        if (!cancelled) {
          setDetail(next)
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          onError(err instanceof Error ? err.message : String(err))
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingDetail(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [selectedId, onError])

  if (selectedId) {
    return (
      <div className="agent-calls">
        <div className="agent-calls-back">
          <Button type="link" size="small" onClick={() => setSelectedId('')}>
            返回列表
          </Button>
        </div>
        <div className="agent-calls-detail">
          {loadingDetail && detail?.call.id !== selectedId ? (
            <div className="trace-loading">
              <Spin />
            </div>
          ) : null}
          {detail && detail.call.id === selectedId ? <TraceView detail={detail} compact /> : null}
        </div>
      </div>
    )
  }

  return (
    <div className="agent-calls">
      <div className="trace-list">
        {loadingList && calls.length === 0 ? (
          <div className="trace-loading">
            <Spin />
          </div>
        ) : null}
        {!loadingList && calls.length === 0 ? (
          <Empty description="这个对话还没有调用记录。" />
        ) : null}
        {calls.map((call) => (
          <button key={call.id} type="button" className="trace-list-item" onClick={() => setSelectedId(call.id)}>
            <span>
              <strong>{call.userInput || '空消息'}</strong>
              <Tag color={call.status === 'SUCCESS' ? 'success' : 'error'}>{call.status === 'SUCCESS' ? '成功' : '失败'}</Tag>
            </span>
            <small>
              {formatTime(call.startedAt)} · {formatDuration(call.latencyMs)} · {call.totalTokens} token
            </small>
          </button>
        ))}
      </div>
    </div>
  )
}
