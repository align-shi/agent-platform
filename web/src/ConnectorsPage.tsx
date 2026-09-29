import { useState } from 'react'
import { Button, Card, Modal, Space, Tag, Typography } from 'antd'
import { HttpToolsSection } from './HttpToolsPage'
import { RemoteMcpSection } from './RemoteMcpPage'
import type { HttpConnector, RemoteMcp } from './api'

const INTERNAL_TOOLS = [
  { name: 'load_skill', detail: '读取已绑定技能的 SKILL.md，并列出参考文件与脚本' },
  { name: 'read_skill_resource', detail: '读取技能目录中的参考文件或模板' },
  { name: 'run_skill_script', detail: '执行技能 scripts/ 下的脚本，返回 stdout' },
]

export function ConnectorsPage({
  tools,
  remoteMcps,
  onChanged,
  onError,
}: {
  tools: HttpConnector[]
  remoteMcps: RemoteMcp[]
  onChanged: () => Promise<void>
  onError: (message: string) => void
}) {
  const [internalOpen, setInternalOpen] = useState(false)

  return (
    <div>
      <Typography.Title level={3} style={{ margin: 0 }}>
        连接器 MCP
      </Typography.Title>
      <Typography.Paragraph type="secondary" style={{ margin: '8px 0 24px' }}>
        三类入口放在同一页：平台内置能力、登记普通 HTTP，以及对接到你自己的 MCP 服务。
      </Typography.Paragraph>

      <div style={{ marginBottom: 28 }}>
        <Typography.Title level={5} style={{ margin: '0 0 4px' }}>
          内部连接器
        </Typography.Title>
        <Typography.Paragraph type="secondary">
          Hub 自带模块，给智能体和技能运行时用，不作为对外 MCP 地址开放。
        </Typography.Paragraph>
        <div className="card-grid">
          <Card
            actions={[
              <Button key="view" type="link" onClick={() => setInternalOpen(true)}>
                查看工具
              </Button>,
            ]}
          >
            <Space vertical size={8}>
              <Tag color="blue">内置</Tag>
              <Typography.Title level={5} style={{ margin: 0 }}>
                技能运行时
              </Typography.Title>
              <Typography.Text type="secondary">
                按需加载 SKILL.md、参考文件并执行脚本。仅随绑定技能对当前智能体开放。
              </Typography.Text>
              <Space>
                <Tag>3 个工具</Tag>
                <Tag>无需登录</Tag>
              </Space>
            </Space>
          </Card>
        </div>
      </div>

      <div style={{ marginBottom: 28 }}>
        <HttpToolsSection tools={tools} onChanged={onChanged} onError={onError} />
      </div>

      <div>
        <RemoteMcpSection servers={remoteMcps} onChanged={onChanged} onError={onError} />
      </div>

      <Modal title="技能运行时" open={internalOpen} onCancel={() => setInternalOpen(false)} footer={null}>
        <Typography.Paragraph type="secondary">智能体绑定技能后，模型可调用这些内置工具。</Typography.Paragraph>
        <Space vertical size={16} style={{ width: '100%' }}>
          {INTERNAL_TOOLS.map((item) => (
            <div key={item.name}>
              <Typography.Text strong>{item.name}</Typography.Text>
              <Typography.Paragraph type="secondary" style={{ margin: '4px 0 0' }}>
                {item.detail}
              </Typography.Paragraph>
            </div>
          ))}
        </Space>
      </Modal>
    </div>
  )
}
