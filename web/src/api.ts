async function throwApiError(res: Response): Promise<never> {
  const text = await res.text()
  try {
    const json = JSON.parse(text) as { message?: string; detail?: string; error?: string; path?: string }
    const message = json.message || json.detail
    if (message && message !== 'Internal Server Error') {
      throw new Error(message)
    }
    if (json.error && json.error !== 'Internal Server Error') {
      throw new Error(json.error)
    }
  } catch (err) {
    if (err instanceof Error && err.name !== 'SyntaxError') {
      throw err
    }
  }
  throw new Error(text || `请求失败（${res.status}）`)
}

export type ProviderType = 'CHAT' | 'EMBEDDING'

export type Provider = {
  id: string
  name: string
  baseUrl: string
  type: ProviderType
  vendor?: string | null
  defaultModel?: string | null
  dimensions?: number | null
  keyLast4: string
  keyConfigured: boolean
}

export type VendorPreset = {
  id: string
  name: string
  baseUrl: string
  defaultModel: string
  type: ProviderType
  dimensions?: number | null
}

export type UpsertProvider = {
  name: string
  baseUrl: string
  apiKey?: string
  type: ProviderType
  vendor?: string
  defaultModel?: string
  dimensions?: number | null
}

export async function listVendors(): Promise<VendorPreset[]> {
  const res = await fetch('/api/vendors')
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function listProviders(): Promise<Provider[]> {
  const res = await fetch('/api/providers')
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function createProvider(body: UpsertProvider): Promise<Provider> {
  const res = await fetch('/api/providers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function updateProvider(id: string, body: UpsertProvider): Promise<Provider> {
  const res = await fetch(`/api/providers/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteProvider(id: string): Promise<void> {
  const res = await fetch(`/api/providers/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export type KnowledgeCitation = {
  knowledgeBase: string
  document: string
  content: string
}

export type ChatMessage = {
  role: 'user' | 'assistant' | 'system'
  content: string
  citations?: KnowledgeCitation[]
}

export type MemoryMode = 'NONE' | 'SESSION' | 'CROSS'

export type Agent = {
  id: string
  name: string
  code: string
  systemPrompt: string
  providerId: string
  providerName: string
  model: string
  skillIds: string[]
  httpToolIds: string[]
  mcpServerIds: string[]
  knowledgeBaseIds: string[]
  memoryMode: MemoryMode
  summarizeWhenTokens: number
  keepLastMessages: number
  memoryFacts: string
}

export type UpsertAgent = {
  name: string
  code: string
  systemPrompt: string
  providerId: string
  model: string
  skillIds: string[]
  httpToolIds: string[]
  mcpServerIds: string[]
  knowledgeBaseIds: string[]
  memoryMode: MemoryMode
  summarizeWhenTokens: number
  keepLastMessages: number
}

export type Skill = {
  id: string
  name: string
  description: string
  body: string
  latestVersion: number
}

export type UpsertSkill = {
  id?: string
  name: string
  description: string
  body: string
}

export type Conversation = {
  id: string
  agentId: string
  title: string
  updatedAt: string
}

export async function listAgents(): Promise<Agent[]> {
  const res = await fetch('/api/agents')
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function createAgent(body: UpsertAgent): Promise<Agent> {
  const res = await fetch('/api/agents', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function updateAgent(id: string, body: UpsertAgent): Promise<Agent> {
  const res = await fetch(`/api/agents/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteAgent(id: string): Promise<void> {
  const res = await fetch(`/api/agents/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export async function clearAgentMemoryFacts(id: string): Promise<Agent> {
  const res = await fetch(`/api/agents/${id}/memory-facts`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export type KnowledgeBase = {
  id: string
  name: string
  description: string
  embeddingProviderId: string
  embeddingProviderName: string
  embeddingModel: string
  embeddingDim?: number | null
  topK: number
  chunkSize: number
  chunkOverlap: number
  documentCount: number
}

export type UpsertKnowledgeBase = {
  name: string
  description: string
  embeddingProviderId: string
  topK: number
  chunkSize: number
  chunkOverlap: number
}

export type KnowledgeDocument = {
  id: string
  knowledgeBaseId: string
  name: string
  status: string
  errorMessage: string
  charCount: number
  chunkCount: number
}

export async function listKnowledgeBases(): Promise<KnowledgeBase[]> {
  const res = await fetch('/api/knowledge-bases')
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function createKnowledgeBase(body: UpsertKnowledgeBase): Promise<KnowledgeBase> {
  const res = await fetch('/api/knowledge-bases', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function updateKnowledgeBase(id: string, body: UpsertKnowledgeBase): Promise<KnowledgeBase> {
  const res = await fetch(`/api/knowledge-bases/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteKnowledgeBase(id: string): Promise<void> {
  const res = await fetch(`/api/knowledge-bases/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export async function listKnowledgeDocuments(id: string): Promise<KnowledgeDocument[]> {
  const res = await fetch(`/api/knowledge-bases/${id}/documents`)
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function addKnowledgeDocument(id: string, name: string, content: string): Promise<KnowledgeDocument> {
  const res = await fetch(`/api/knowledge-bases/${id}/documents`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, content }),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteKnowledgeDocument(id: string, documentId: string): Promise<void> {
  const res = await fetch(`/api/knowledge-bases/${id}/documents/${documentId}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export type HttpParamIn = 'query' | 'path' | 'header' | 'body'
export type HttpParamType = 'string' | 'number' | 'integer' | 'boolean'
export type HttpAuthType = 'NONE' | 'BEARER' | 'HEADER'

export type HttpToolParam = {
  name: string
  in: HttpParamIn
  type: HttpParamType
  required: boolean
  description: string
}

export type HttpConnectorTool = {
  id: string
  name: string
  toolName: string
  description: string
  method: string
  url: string
  enabled: boolean
  parameters: HttpToolParam[]
}

export type HttpConnector = {
  id: string
  name: string
  description: string
  protocol: string
  authType: HttpAuthType
  authHeader: string
  secretConfigured: boolean
  keyLast4: string
  timeoutMs: number
  enabled: boolean
  forwardCredentials: boolean
  tools: HttpConnectorTool[]
}

export type UpsertHttpConnectorTool = {
  id?: string
  name: string
  toolName: string
  description: string
  method: string
  url: string
  enabled: boolean
  parameters: HttpToolParam[]
}

export type UpsertHttpConnector = {
  name: string
  description: string
  protocol?: string
  authType: HttpAuthType
  authHeader: string
  secret?: string
  timeoutMs: number
  enabled: boolean
  forwardCredentials: boolean
  tools: UpsertHttpConnectorTool[]
}

export async function listHttpTools(): Promise<HttpConnector[]> {
  const res = await fetch('/api/http-tools')
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function createHttpTool(body: UpsertHttpConnector): Promise<HttpConnector> {
  const res = await fetch('/api/http-tools', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function updateHttpTool(id: string, body: UpsertHttpConnector): Promise<HttpConnector> {
  const res = await fetch(`/api/http-tools/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteHttpTool(id: string): Promise<void> {
  const res = await fetch(`/api/http-tools/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export async function tryHttpTool(
  connectorId: string,
  toolId: string,
  argumentsMap: Record<string, string>,
  secret?: string,
): Promise<string> {
  const res = await fetch(`/api/http-tools/${connectorId}/tools/${toolId}/try`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      arguments: argumentsMap,
      ...(secret ? { secret } : {}),
    }),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  const data = (await res.json()) as { output: string }
  return data.output
}

export type McpTransport = 'STREAMABLE' | 'SSE'

export type RemoteMcpTool = {
  name: string
  description: string
}

export type RemoteMcp = {
  id: string
  name: string
  description: string
  url: string
  transport: McpTransport
  authType: HttpAuthType
  authHeader: string
  secretConfigured: boolean
  keyLast4: string
  timeoutMs: number
  enabled: boolean
  serverName: string
  serverVersion: string
  protocolVersion: string
  lastError: string
  lastSyncedAt: string
  tools: RemoteMcpTool[]
}

export type UpsertRemoteMcp = {
  name: string
  description: string
  url: string
  transport: McpTransport
  authType: HttpAuthType
  authHeader: string
  secret?: string
  timeoutMs: number
  enabled: boolean
}

export async function listRemoteMcps(): Promise<RemoteMcp[]> {
  const res = await fetch('/api/remote-mcps')
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function createRemoteMcp(body: UpsertRemoteMcp): Promise<RemoteMcp> {
  const res = await fetch('/api/remote-mcps', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function updateRemoteMcp(id: string, body: UpsertRemoteMcp): Promise<RemoteMcp> {
  const res = await fetch(`/api/remote-mcps/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteRemoteMcp(id: string): Promise<void> {
  const res = await fetch(`/api/remote-mcps/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export async function refreshRemoteMcp(id: string, secret?: string): Promise<RemoteMcp> {
  const res = await fetch(`/api/remote-mcps/${id}/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(secret ? { secret } : {}),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function tryRemoteMcp(id: string, secret?: string): Promise<string> {
  const res = await fetch(`/api/remote-mcps/${id}/try`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(secret ? { secret } : {}),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  const data = (await res.json()) as { output: string }
  return data.output
}

export async function listSkills(): Promise<Skill[]> {
  const res = await fetch('/api/skills')
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export type GeneratedSkillFile = {
  path: string
  content: string
}

export type GeneratedSkill = {
  name: string
  code: string
  description: string
  body: string
  files: GeneratedSkillFile[]
  skipped: string[]
  providerName: string
  model: string
}

export async function generateSkill(
  brief: string,
  current?: { skillId: string; files?: GeneratedSkillFile[] },
): Promise<GeneratedSkill> {
  const res = await fetch('/api/skills/generate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      brief,
      ...(current?.skillId ? { skillId: current.skillId, files: current.files ?? [] } : {}),
    }),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function createSkill(body: UpsertSkill): Promise<Skill> {
  const res = await fetch('/api/skills', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function updateSkill(id: string, body: UpsertSkill): Promise<Skill> {
  const res = await fetch(`/api/skills/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteSkill(id: string): Promise<void> {
  const res = await fetch(`/api/skills/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export type SkillVersion = {
  version: number
  createdAt: string
}

export async function listSkillFiles(id: string, version?: number | null): Promise<string[]> {
  const query = version ? `?version=${version}` : ''
  const res = await fetch(`/api/skills/${id}/files${query}`)
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function readSkillFile(
  id: string,
  path: string,
  version?: number | null,
): Promise<{ path: string; content: string }> {
  const params = new URLSearchParams({ path })
  if (version) {
    params.set('version', String(version))
  }
  const res = await fetch(`/api/skills/${id}/file?${params}`)
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function writeSkillFile(
  id: string,
  path: string,
  content: string,
): Promise<{ path: string; content: string }> {
  const res = await fetch(`/api/skills/${id}/file`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path, content }),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteSkillFile(id: string, path: string): Promise<void> {
  const res = await fetch(`/api/skills/${id}/file?path=${encodeURIComponent(path)}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export async function createSkillDir(id: string, path: string): Promise<void> {
  const res = await fetch(`/api/skills/${id}/dirs`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ path }),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export async function renameSkillFile(id: string, from: string, to: string): Promise<{ path: string; content: string }> {
  const res = await fetch(`/api/skills/${id}/rename`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ from, to }),
  })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function listSkillVersions(id: string): Promise<SkillVersion[]> {
  const res = await fetch(`/api/skills/${id}/versions`)
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function publishSkillVersion(id: string): Promise<SkillVersion> {
  const res = await fetch(`/api/skills/${id}/versions`, { method: 'POST' })
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function restoreSkillVersion(id: string, version: number): Promise<void> {
  const res = await fetch(`/api/skills/${id}/versions/${version}/restore`, { method: 'POST' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export async function listConversations(agentId: string): Promise<Conversation[]> {
  const res = await fetch(`/api/agents/${agentId}/conversations`)
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function listMessages(conversationId: string): Promise<ChatMessage[]> {
  const res = await fetch(`/api/conversations/${conversationId}/messages`)
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function deleteConversation(id: string): Promise<void> {
  const res = await fetch(`/api/conversations/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    await throwApiError(res)
  }
}

export type CallSummary = {
  id: string
  agentId: string
  agentName: string
  conversationId: string
  model: string
  status: 'SUCCESS' | 'ERROR' | string
  userInput: string
  startedAt: string
  latencyMs: number
  rounds: number
  modelCalls: number
  toolCalls: number
  mcpCalls: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  error?: string | null
}

export type CallSection = {
  key: string
  label: string
  text: string
}

export type CallSpan = {
  id: string
  kind: string
  title: string
  summary: string
  offsetMs: number
  durationMs: number
  status: string
  sections: CallSection[]
  children: CallSpan[]
}

export type CallDetail = {
  call: CallSummary
  spans: CallSpan[]
}

export async function listCalls(agentId?: string): Promise<CallSummary[]> {
  const query = agentId ? `?agentId=${encodeURIComponent(agentId)}` : ''
  const res = await fetch(`/api/calls${query}`)
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

export async function getCall(id: string): Promise<CallDetail> {
  const res = await fetch(`/api/calls/${id}`)
  if (!res.ok) {
    await throwApiError(res)
  }
  return res.json()
}

function parseSseBlock(block: string): { event: string; data: string } | null {
  const lines = block.split('\n')
  let event = 'message'
  const data: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      data.push(line.slice(5).trim())
    }
  }
  if (data.length === 0) {
    return null
  }
  return { event, data: data.join('\n') }
}

function extractDelta(payload: string): string {
  try {
    const json = JSON.parse(payload) as {
      choices?: Array<{ delta?: { content?: string }; message?: { content?: string } }>
    }
    return json.choices?.[0]?.delta?.content ?? json.choices?.[0]?.message?.content ?? ''
  } catch {
    return ''
  }
}

export async function streamChat(
  input: { agentId: string; conversationId?: string; content: string },
  onDelta: (text: string) => void,
  onMeta?: (conversationId: string) => void,
  onStatus?: (text: string) => void,
  onCitations?: (citations: KnowledgeCitation[]) => void,
): Promise<void> {
  const res = await fetch('/api/chat/completions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(input),
  })
  if (!res.ok || !res.body) {
    throw new Error((await res.text()) || `HTTP ${res.status}`)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() ?? ''
    for (const part of parts) {
      const parsed = parseSseBlock(part)
      if (!parsed) {
        continue
      }
      if (parsed.event === 'error') {
        throw new Error(parsed.data)
      }
      if (parsed.event === 'meta') {
        try {
          const meta = JSON.parse(parsed.data) as { conversationId?: string }
          if (meta.conversationId) {
            onMeta?.(meta.conversationId)
          }
        } catch {
          // ignore malformed meta
        }
        continue
      }
      if (parsed.event === 'status') {
        onStatus?.(parsed.data)
        continue
      }
      if (parsed.event === 'citations') {
        try {
          const citations = JSON.parse(parsed.data) as KnowledgeCitation[]
          if (Array.isArray(citations)) {
            onCitations?.(citations)
          }
        } catch {
          // ignore malformed citations
        }
        continue
      }
      if (parsed.event === 'done' || parsed.data === '[DONE]') {
        return
      }
      const delta = extractDelta(parsed.data)
      if (delta) {
        onDelta(delta)
      }
    }
  }
}
