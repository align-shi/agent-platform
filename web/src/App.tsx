import { useEffect, useState } from 'react'
import { Layout, Menu, Modal } from 'antd'
import type { MenuProps } from 'antd'
import {
  ApiOutlined,
  ApartmentOutlined,
  CloudOutlined,
  DatabaseOutlined,
  MessageOutlined,
  RobotOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { listAgents, listHttpTools, listKnowledgeBases, listProviders, listRemoteMcps, listSkills, listWorkflows, type Agent, type HttpConnector, type KnowledgeBase, type Provider, type RemoteMcp, type Skill, type Workflow } from './api'
import { AgentsPage } from './AgentsPage'
import { ConnectorsPage } from './ConnectorsPage'
import { FeishuPage } from './FeishuPage'
import { WorkflowsPage } from './WorkflowsPage'
import { KnowledgePage } from './KnowledgePage'
import { ProvidersPage } from './ProvidersPage'
import { SkillsPage } from './SkillsPage'

type NavId = 'providers' | 'agents' | 'skills' | 'connectors' | 'knowledge' | 'feishu' | 'workflows'

const NAV_IDS: NavId[] = ['agents', 'skills', 'connectors', 'knowledge', 'workflows', 'feishu', 'providers']

function pageFromHash(): NavId {
  const raw = window.location.hash.replace(/^#\/?/, '')
  return NAV_IDS.includes(raw as NavId) ? (raw as NavId) : 'agents'
}

const NAV_ITEMS: MenuProps['items'] = [
  { key: 'agents', icon: <RobotOutlined />, label: '智能体 Agent' },
  { key: 'skills', icon: <ThunderboltOutlined />, label: '技能 Skill' },
  { key: 'connectors', icon: <ApiOutlined />, label: '连接器 MCP' },
  { key: 'knowledge', icon: <DatabaseOutlined />, label: '知识库 Knowledge' },
  { key: 'workflows', icon: <ApartmentOutlined />, label: '工作流 Workflow' },
  { key: 'feishu', icon: <MessageOutlined />, label: '飞书 Feishu' },
  { key: 'providers', icon: <CloudOutlined />, label: '模型提供商 Providers' },
]

export default function App() {
  const [page, setPage] = useState<NavId>(pageFromHash)
  const [providers, setProviders] = useState<Provider[]>([])
  const [agents, setAgents] = useState<Agent[]>([])
  const [skills, setSkills] = useState<Skill[]>([])
  const [httpTools, setHttpTools] = useState<HttpConnector[]>([])
  const [remoteMcps, setRemoteMcps] = useState<RemoteMcp[]>([])
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBase[]>([])
  const [workflows, setWorkflows] = useState<Workflow[]>([])
  const [error, setError] = useState('')

  function goTo(next: NavId) {
    setPage(next)
    const hash = `#${next}`
    if (window.location.hash !== hash) {
      window.location.hash = next
    }
  }

  async function refresh() {
    try {
      setError('')
      const [nextProviders, nextAgents, nextSkills, nextHttpTools, nextRemoteMcps, nextKnowledge, nextWorkflows] = await Promise.all([
        listProviders(),
        listAgents(),
        listSkills(),
        listHttpTools(),
        listRemoteMcps(),
        listKnowledgeBases(),
        listWorkflows(),
      ])
      setProviders(nextProviders)
      setAgents(nextAgents)
      setSkills(nextSkills)
      setHttpTools(nextHttpTools)
      setRemoteMcps(nextRemoteMcps)
      setKnowledgeBases(nextKnowledge)
      setWorkflows(nextWorkflows)
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err))
    }
  }

  useEffect(() => {
    void refresh()
  }, [])

  useEffect(() => {
    function onHashChange() {
      setPage(pageFromHash())
    }
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  }, [])

  return (
    <Layout style={{ minHeight: '100%' }}>
      <Layout.Sider width={232} className="app-sider" theme="light" style={{ position: 'sticky', top: 0, height: '100vh', overflow: 'auto' }}>
        <div className="app-brand">Agent Platform</div>
        <Menu
          mode="inline"
          selectedKeys={[page]}
          items={NAV_ITEMS}
          onClick={({ key }) => {
            if (NAV_IDS.includes(key as NavId)) {
              goTo(key as NavId)
            }
          }}
        />
      </Layout.Sider>
      <Layout.Content className="app-content">
        <Modal
          title="出错了"
          open={Boolean(error)}
          zIndex={2000}
          okText="知道了"
          cancelButtonProps={{ style: { display: 'none' } }}
          onOk={() => setError('')}
          onCancel={() => setError('')}
        >
          <div style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{error}</div>
        </Modal>
        {page === 'providers' ? (
          <ProvidersPage providers={providers} onChanged={refresh} onError={setError} />
        ) : null}
        {page === 'agents' ? (
          <AgentsPage
            agents={agents}
            providers={providers}
            skills={skills}
            httpTools={httpTools}
            remoteMcps={remoteMcps}
            knowledgeBases={knowledgeBases}
            workflows={workflows}
            onChanged={refresh}
            onError={setError}
          />
        ) : null}
        {page === 'knowledge' ? (
          <KnowledgePage bases={knowledgeBases} providers={providers} onChanged={refresh} onError={setError} />
        ) : null}
        {page === 'connectors' ? (
          <ConnectorsPage tools={httpTools} remoteMcps={remoteMcps} onChanged={refresh} onError={setError} />
        ) : null}
        {page === 'skills' ? (
          <SkillsPage skills={skills} onChanged={refresh} onError={setError} />
        ) : null}
        {page === 'workflows' ? <WorkflowsPage agents={agents} providers={providers} httpTools={httpTools} onChanged={refresh} onError={setError} /> : null}
        {page === 'feishu' ? <FeishuPage agents={agents} onError={setError} /> : null}
      </Layout.Content>
    </Layout>
  )
}
