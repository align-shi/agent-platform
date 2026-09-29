import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Button, Card, Empty, Flex, Form, Input, Modal, Popconfirm, Radio, Space, Table, Tag, Typography, message } from 'antd'
import {
  createSkill,
  createSkillDir,
  generateSkill,
  deleteSkill,
  deleteSkillFile,
  listSkillFiles,
  listSkillVersions,
  publishSkillVersion,
  readSkillFile,
  renameSkillFile,
  restoreSkillVersion,
  updateSkill,
  writeSkillFile,
  type Skill,
  type SkillVersion,
} from './api'

const FRESH_SKILL_BODY = '# 新技能\n\n在此写步骤。需要参考文件或脚本时，再自己新建文件夹和文件。\n'

export function SkillsPage({
  skills,
  onChanged,
  onError,
}: {
  skills: Skill[]
  onChanged: () => Promise<void>
  onError: (message: string) => void
}) {
  const [view, setView] = useState<'list' | 'create' | string>('list')
  const [id, setId] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [body, setBody] = useState('')
  const [entries, setEntries] = useState<string[]>([])
  const [selectedPath, setSelectedPath] = useState('SKILL.md')
  const [selectedDir, setSelectedDir] = useState('')
  const [pickedDir, setPickedDir] = useState(false)
  const [fileContent, setFileContent] = useState('')
  const [versions, setVersions] = useState<SkillVersion[]>([])
  const [viewVersion, setViewVersion] = useState<number | null>(null)
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({})
  const [saving, setSaving] = useState(false)
  const [generating, setGenerating] = useState(false)
  const [brief, setBrief] = useState('')
  const [keyword, setKeyword] = useState('')
  const [versionsOpen, setVersionsOpen] = useState(false)
  const [newOpen, setNewOpen] = useState(false)
  const [newKind, setNewKind] = useState<'file' | 'dir'>('file')
  const [newPath, setNewPath] = useState('')
  const uploadRef = useRef<HTMLInputElement>(null)
  const draftRef = useRef<Record<string, string>>({})
  const editingId = view !== 'list' && view !== 'create' ? view : null
  const creating = view === 'create'
  const current = skills.find((item) => item.id === editingId)
  const readonly = viewVersion != null

  useEffect(() => {
    if (!editingId) {
      return
    }
    const skill = skills.find((item) => item.id === editingId)
    if (!skill) {
      return
    }
    setId(skill.id)
    setName(skill.name)
    setDescription(skill.description ?? '')
    setBody(skill.body ?? '')
  }, [editingId, skills])

  useEffect(() => {
    if (!editingId) {
      return
    }
    void refreshDetail(editingId, viewVersion).catch((err: unknown) =>
      onError(err instanceof Error ? err.message : String(err)),
    )
  }, [editingId, viewVersion, onError])

  async function refreshDetail(skillId: string, version: number | null) {
    const [nextEntries, nextVersions] = await Promise.all([
      listSkillFiles(skillId, version),
      listSkillVersions(skillId),
    ])
    setEntries(nextEntries)
    setVersions(nextVersions)
    const nextPath = selectedPath && nextEntries.some((entry) => entry.replace(/\/$/, '') === selectedPath)
      ? selectedPath
      : 'SKILL.md'
    setSelectedPath(nextPath)
    await loadPath(skillId, nextPath, version)
  }

  async function loadPath(skillId: string, path: string, version: number | null) {
    const file = await readSkillFile(skillId, path, version)
    if (path === 'SKILL.md') {
      const match = file.content.match(/^---[\s\S]*?\n---\s*\n?/)
      setFileContent(match ? file.content.slice(match[0].length) : file.content)
    } else {
      setFileContent(file.content)
    }
  }

  function resetDetail() {
    setId('')
    setName('')
    setDescription('')
    setBody('')
    setEntries(['SKILL.md'])
    setSelectedPath('SKILL.md')
    setSelectedDir('')
    setPickedDir(false)
    setFileContent('')
    draftRef.current = {}
    setNewOpen(false)
    setVersions([])
    setViewVersion(null)
    setCollapsed({})
    setVersionsOpen(false)
    setBrief('')
    setGenerating(false)
  }

  function openSkill(skill: Skill) {
    resetDetail()
    setId(skill.id)
    setName(skill.name)
    setDescription(skill.description ?? '')
    setBody(skill.body ?? '')
    setView(skill.id)
  }

  function openCreate() {
    resetDetail()
    const initial = FRESH_SKILL_BODY
    draftRef.current = { 'SKILL.md': initial }
    setBody(initial)
    setFileContent(initial)
    setEntries(['SKILL.md'])
    setSelectedPath('SKILL.md')
    setView('create')
  }

  function backToList() {
    resetDetail()
    setView('list')
  }

  function currentBody() {
    if (selectedPath === 'SKILL.md' && !pickedDir) {
      return fileContent
    }
    return draftRef.current['SKILL.md'] ?? body
  }

  function generatedWouldOverwrite() {
    if (!creating) {
      return true
    }
    const extra = entries.some((entry) => entry !== 'SKILL.md')
    const text = currentBody()
    return extra || (text.trim() !== '' && text !== FRESH_SKILL_BODY)
  }

  async function applyGenerated(generated: Awaited<ReturnType<typeof generateSkill>>) {
    if (!name.trim() && generated.name) {
      setName(generated.name)
    }
    if (!description.trim() && generated.description) {
      setDescription(generated.description)
    }
    if (creating && !id.trim() && /^[a-z0-9-]+$/.test(generated.code || '')) {
      setId(generated.code)
    }
    const nextBody = generated.body || ''
    const files = generated.files ?? []
    if (creating) {
      const nextDraft: Record<string, string> = { 'SKILL.md': nextBody }
      const nextEntries = ['SKILL.md']
      for (const file of files) {
        nextDraft[file.path] = file.content ?? ''
        nextEntries.push(file.path)
      }
      draftRef.current = nextDraft
      setEntries(nextEntries)
      setBody(nextBody)
      setFileContent(nextBody)
      setSelectedPath('SKILL.md')
      setSelectedDir('')
      setPickedDir(false)
      return
    }
    if (!editingId) {
      return
    }
    const nextName = generated.name?.trim() || name
    const nextDescription = generated.description?.trim() || description
    await updateSkill(editingId, {
      id: editingId,
      name: nextName,
      description: nextDescription,
      body: nextBody,
    })
    for (const file of files) {
      await writeSkillFile(editingId, file.path, file.content ?? '')
    }
    setName(nextName)
    setDescription(nextDescription)
    setBody(nextBody)
    setFileContent(nextBody)
    setSelectedPath('SKILL.md')
    setSelectedDir('')
    setPickedDir(false)
    await onChanged()
    await refreshDetail(editingId, null)
  }

  async function onGenerate() {
    if (readonly) {
      return
    }
    if (!brief.trim()) {
      onError('请先填写要生成的技能说明')
      return
    }
    const confirmText = creating
      ? '生成结果会覆盖当前的 SKILL.md，并写入生成的参考文件。继续吗？'
      : '会按你的说明修改当前技能，并写回 SKILL.md。模型改过的参考文件会更新，没提到的文件会保留。继续吗？'
    if (generatedWouldOverwrite() && !window.confirm(confirmText)) {
      return
    }
    setGenerating(true)
    try {
      const openFile = !creating && editingId && !pickedDir && selectedPath
        ? [{ path: selectedPath, content: fileContent }]
        : []
      const generated = await generateSkill(
        brief.trim(),
        creating || !editingId ? undefined : { skillId: editingId, files: openFile },
      )
      await applyGenerated(generated)
      const skipped = generated.skipped?.length ? `，已跳过 ${generated.skipped.length} 个不符合规范的文件` : ''
      message.success(`已用 ${generated.providerName} / ${generated.model} ${creating ? '生成' : '修改'}${skipped}`)
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setGenerating(false)
    }
  }

  async function createNew() {
    const skillId = id.trim().toLowerCase()
    if (!/^[a-z0-9-]+$/.test(skillId)) {
      onError('编码只能包含小写字母、数字和中划线')
      return
    }
    if (!name.trim()) {
      onError('请填写名称')
      return
    }
    setSaving(true)
    try {
      const contents = { ...draftRef.current }
      if (!pickedDir) {
        contents[selectedPath] = fileContent
      }
      const created = await createSkill({
        id: skillId,
        name: name.trim(),
        description,
        body: contents['SKILL.md'] ?? body,
      })
      try {
        const dirs = entries
          .filter((entry) => entry.endsWith('/'))
          .map((entry) => entry.slice(0, -1))
          .sort((a, b) => a.length - b.length)
        for (const dir of dirs) {
          await createSkillDir(created.id, dir)
        }
        for (const path of entries.filter((entry) => !entry.endsWith('/') && entry !== 'SKILL.md')) {
          await writeSkillFile(created.id, path, contents[path] ?? '')
        }
      } finally {
        await onChanged()
        setId(created.id)
        setView(created.id)
      }
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  async function saveCurrentFile() {
    if (creating) {
      draftRef.current[selectedPath] = fileContent
      if (selectedPath === 'SKILL.md') {
        setBody(fileContent)
      }
      message.success('已记在草稿中，创建技能时会一并保存')
      return
    }
    if (!editingId || readonly) {
      return
    }
    setSaving(true)
    try {
      await updateSkill(editingId, {
        id: editingId,
        name,
        description,
        body: selectedPath === 'SKILL.md' ? fileContent : body,
      })
      if (selectedPath === 'SKILL.md') {
        setBody(fileContent)
      } else {
        await writeSkillFile(editingId, selectedPath, fileContent)
      }
      await onChanged()
      await refreshDetail(editingId, null)
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    } finally {
      setSaving(false)
    }
  }

  function openNewDialog() {
    if (readonly || (!creating && !editingId)) {
      return
    }
    setNewKind('file')
    setNewPath(selectedDir ? `${selectedDir}/` : '')
    setNewOpen(true)
  }

  async function confirmNew() {
    const parsed = parseSkillPath(newPath, newKind === 'dir')
    if ('error' in parsed) {
      onError(parsed.error)
      return
    }
    const path = parsed.path
    const directory = newKind === 'dir'
    if (pathExists(entries, path, directory)) {
      onError(directory ? '文件夹已存在' : '文件已存在')
      return
    }
    try {
      if (creating) {
        draftRef.current[selectedPath] = fileContent
        if (directory) {
          setEntries((currentEntries) => [...currentEntries, `${path}/`])
          setSelectedDir(path)
          setPickedDir(true)
        } else {
          draftRef.current[path] = ''
          setEntries((currentEntries) => [...currentEntries, path])
          setSelectedPath(path)
          setSelectedDir(path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '')
          setPickedDir(false)
          setFileContent('')
        }
      } else if (editingId) {
        if (directory) {
          await createSkillDir(editingId, path)
          setSelectedDir(path)
          setPickedDir(true)
        } else {
          await writeSkillFile(editingId, path, '')
          setSelectedPath(path)
          setPickedDir(false)
          setFileContent('')
        }
        await refreshDetail(editingId, null)
      }
      setNewOpen(false)
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function onRename() {
    if (readonly || (!creating && !editingId)) {
      return
    }
    const from = pickedDir ? selectedDir : selectedPath
    const directory = pickedDir
    if (!from || from === 'SKILL.md') {
      onError('请先选择要重命名的文件或目录')
      return
    }
    const to = window.prompt('新路径', from)
    if (!to || to === from) {
      return
    }
    const parsed = parseSkillPath(to, directory)
    if ('error' in parsed) {
      onError(parsed.error)
      return
    }
    if (parsed.path === from) {
      return
    }
    if (pathExists(entries, parsed.path, directory)) {
      onError(directory ? '文件夹已存在' : '文件已存在')
      return
    }
    try {
      if (creating) {
        draftRef.current[selectedPath] = fileContent
        setEntries((currentEntries) => currentEntries.map((entry) => rewriteEntry(entry, from, parsed.path)))
        const nextDraft: Record<string, string> = {}
        for (const [key, value] of Object.entries(draftRef.current)) {
          nextDraft[rewriteEntry(key, from, parsed.path)] = value
        }
        draftRef.current = nextDraft
        if (!directory) {
          setSelectedPath(parsed.path)
          setFileContent(nextDraft[parsed.path] ?? '')
        }
        setSelectedDir(directory ? parsed.path : parsed.path.includes('/') ? parsed.path.slice(0, parsed.path.lastIndexOf('/')) : '')
      } else if (editingId) {
        await renameSkillFile(editingId, from, parsed.path)
        setSelectedPath(directory ? 'SKILL.md' : parsed.path)
        setSelectedDir(directory ? parsed.path : '')
        await refreshDetail(editingId, null)
      }
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function onDeleteEntry() {
    if (readonly || (!creating && !editingId)) {
      return
    }
    const target = pickedDir ? selectedDir : selectedPath
    if (!target || target === 'SKILL.md') {
      onError('请先选择要删除的文件或目录')
      return
    }
    if (!window.confirm(`删除 ${target}？`)) {
      return
    }
    try {
      if (creating) {
        setEntries((currentEntries) => currentEntries.filter((entry) => !entryMatches(entry, target)))
        for (const key of Object.keys(draftRef.current)) {
          if (entryMatches(key, target)) {
            delete draftRef.current[key]
          }
        }
        setSelectedPath('SKILL.md')
        setSelectedDir('')
        setPickedDir(false)
        setFileContent(draftRef.current['SKILL.md'] ?? '')
      } else if (editingId) {
        await deleteSkillFile(editingId, target)
        setSelectedPath('SKILL.md')
        setSelectedDir('')
        setPickedDir(false)
        await refreshDetail(editingId, null)
      }
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function onUpload(file: File) {
    if (readonly || (!creating && !editingId)) {
      return
    }
    const folder = selectedDir
    const path = folder ? `${folder}/${file.name}` : file.name
    const parsed = parseSkillPath(path, false)
    if ('error' in parsed) {
      onError(parsed.error)
      return
    }
    try {
      const text = await file.text()
      if (creating) {
        draftRef.current[selectedPath] = fileContent
        draftRef.current[parsed.path] = text
        setEntries((currentEntries) =>
          currentEntries.includes(parsed.path) ? currentEntries : [...currentEntries, parsed.path],
        )
        setSelectedPath(parsed.path)
        setFileContent(text)
      } else if (editingId) {
        await writeSkillFile(editingId, parsed.path, text)
        setSelectedPath(parsed.path)
        await refreshDetail(editingId, null)
      }
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function onPublish() {
    if (!editingId || readonly) {
      return
    }
    try {
      await updateSkill(editingId, {
        id: editingId,
        name,
        description,
        body: selectedPath === 'SKILL.md' ? fileContent : body,
      })
      const published = await publishSkillVersion(editingId)
      message.success(`已发布为 v${published.version}，历史版本会保留`)
      setViewVersion(null)
      await onChanged()
      await refreshDetail(editingId, null)
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  async function onRestore(version: number) {
    if (!editingId) {
      return
    }
    try {
      await restoreSkillVersion(editingId, version)
      message.success(`工作副本已还原为 v${version}`)
      setViewVersion(null)
      setVersionsOpen(false)
      await onChanged()
      await refreshDetail(editingId, null)
    } catch (err) {
      onError(err instanceof Error ? err.message : String(err))
    }
  }

  const tree = buildSkillTree(entries)
  const query = keyword.trim().toLowerCase()
  const filtered = query
    ? skills.filter((item) =>
        [item.name, item.id, item.description].some((value) => (value ?? '').toLowerCase().includes(query)),
      )
    : skills
  const latestVersion = current?.latestVersion ?? versions[0]?.version ?? 0
  const nextVersion = latestVersion + 1

  if (view === 'list') {
    return (
      <div>
        <Flex justify="space-between" align="flex-start" style={{ marginBottom: 20 }} gap={12} wrap>
          <div>
            <Typography.Title level={3} style={{ margin: 0 }}>
              技能
            </Typography.Title>
            <Typography.Paragraph type="secondary" style={{ margin: '8px 0 0' }}>
              点开某一行再编辑。发版会升一个版本并保留历史，随时可还原。
            </Typography.Paragraph>
          </div>
          <Space>
            <Input.Search
              allowClear
              placeholder="搜索名称、编码或描述"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              style={{ width: 280 }}
            />
            <Button type="primary" onClick={openCreate}>
              新建技能
            </Button>
          </Space>
        </Flex>
        <Table
          rowKey="id"
          dataSource={filtered}
          pagination={false}
          locale={{ emptyText: <Empty description={query ? '没有匹配的技能' : '还没有技能，新建后可以给智能体绑定。'} /> }}
          onRow={(item) => ({
            onClick: () => openSkill(item),
            style: { cursor: 'pointer' },
          })}
          columns={[
            { title: '名称', dataIndex: 'name', width: 220 },
            { title: '编码', dataIndex: 'id', width: 220 },
            {
              title: '当前版本',
              dataIndex: 'latestVersion',
              width: 120,
              render: (value: number) => (value ? `v${value}` : '未发版'),
            },
            {
              title: '描述',
              dataIndex: 'description',
              ellipsis: true,
              render: (value: string) => value || '未填写描述',
            },
          ]}
        />
      </div>
    )
  }

  return (
    <div>
      <Flex justify="space-between" align="center" style={{ marginBottom: 20 }} gap={12}>
        <Button onClick={backToList}>返回列表</Button>
        <Space>
          {creating ? (
            <Button type="primary" loading={saving} disabled={!id.trim() || !name.trim()} onClick={() => void createNew()}>
              创建
            </Button>
          ) : (
            <>
              {editingId ? (
                <Popconfirm
                  title="删除这个技能？"
                  onConfirm={() => {
                    void deleteSkill(editingId)
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
              <Button onClick={() => setVersionsOpen(true)}>版本历史</Button>
              <Button type="primary" disabled={readonly} onClick={() => void onPublish()}>
                发布为 v{nextVersion}
              </Button>
            </>
          )}
        </Space>
      </Flex>
      {creating || current ? (
        <Card>
          {creating ? (
            <Typography.Paragraph type="secondary">
              新建时只有 SKILL.md。文件夹和文件名自己填写，文件夹每一段只能是字母、数字、点、下划线或中划线，且不能以点开头。scripts 下只能放脚本文件。
            </Typography.Paragraph>
          ) : readonly ? (
            <Typography.Paragraph type="warning">
              正在查看历史版本 v{viewVersion}（只读）。还原后会覆盖当前工作副本，历史版本仍保留。
            </Typography.Paragraph>
          ) : (
            <Typography.Paragraph type="secondary">
              当前工作副本{latestVersion ? `，已发布到 v${latestVersion}` : '，尚未发版'}。每次发布都会新增一个版本，不会覆盖旧版。
            </Typography.Paragraph>
          )}
          <Form layout="vertical">
            <Form.Item label="名称" required>
              <Input value={name} onChange={(e) => setName(e.target.value)} disabled={readonly} placeholder="例如：客户回复" />
            </Form.Item>
            <Form.Item label="编码" required={creating}>
              <Input
                value={id}
                onChange={(e) => setId(e.target.value)}
                disabled={!creating}
                placeholder="customer-reply"
              />
            </Form.Item>
            <Form.Item label="描述">
              <Input.TextArea value={description} onChange={(e) => setDescription(e.target.value)} disabled={readonly} rows={3} />
            </Form.Item>
            {readonly ? null : (
              <Form.Item
                label={creating ? '自动生成' : '按现有内容修改'}
                extra={
                  creating
                    ? '填写这个技能要解决的问题。平台会随机调用一个已配置的 Chat 模型，按技能格式生成草稿并填到下方。'
                    : '填写要改什么。平台会带上当前技能，让模型在这个版本上修改后再写回。正在编辑且未保存的文件会一起带上。'
                }
              >
                <Input.TextArea
                  value={brief}
                  onChange={(e) => setBrief(e.target.value)}
                  placeholder={
                    creating
                      ? '例如：用户投诉或催单时，按固定结构回复，并检查是否漏了处理时限'
                      : '例如：在回复步骤里补上必须先确认工单号'
                  }
                  rows={4}
                />
                <Button style={{ marginTop: 8 }} loading={generating} onClick={() => void onGenerate()}>
                  {creating ? '自动生成' : '按现有内容修改'}
                </Button>
              </Form.Item>
            )}
          </Form>
          <Flex wrap gap={8} style={{ marginBottom: 12 }}>
            <Button disabled={readonly} onClick={openNewDialog}>
              新建
            </Button>
            <Button disabled={readonly} onClick={() => uploadRef.current?.click()}>
              上传
            </Button>
            <Button disabled={readonly} onClick={() => void onRename()}>
              重命名
            </Button>
            <Button danger disabled={readonly} onClick={() => void onDeleteEntry()}>
              删除
            </Button>
            <input
              ref={uploadRef}
              type="file"
              hidden
              onChange={(event) => {
                const file = event.target.files?.[0]
                event.target.value = ''
                if (file) {
                  void onUpload(file)
                }
              }}
            />
          </Flex>
          <div className="skill-workspace">
            <div className="skill-tree">
              {renderSkillTree(tree, {
                selectedPath: pickedDir ? '' : selectedPath,
                selectedDir,
                collapsed,
                onToggle(dir) {
                  setCollapsed((currentState) => ({ ...currentState, [dir]: !currentState[dir] }))
                  setSelectedDir(dir)
                  setPickedDir(true)
                },
                onSelect(path) {
                  if (creating) {
                    setFileContent(draftRef.current[path] ?? '')
                  }
                  setSelectedPath(path)
                  setSelectedDir(path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : '')
                  setPickedDir(false)
                  if (creating || !editingId) {
                    return
                  }
                  void loadPath(editingId, path, viewVersion).catch((err: unknown) =>
                    onError(err instanceof Error ? err.message : String(err)),
                  )
                },
              })}
            </div>
            <div>
              <Flex justify="space-between" align="center" style={{ marginBottom: 8 }}>
                <Typography.Text strong>{pickedDir ? `${selectedDir}/` : selectedPath}</Typography.Text>
                <Button type="primary" disabled={readonly || saving || pickedDir} loading={saving} onClick={() => void saveCurrentFile()}>
                  保存文件
                </Button>
              </Flex>
              <Input.TextArea
                className="skill-editor-area"
                value={pickedDir ? '' : fileContent}
                onChange={(e) => {
                  const value = e.target.value
                  setFileContent(value)
                  if (creating && !pickedDir) {
                    draftRef.current[selectedPath] = value
                  }
                }}
                disabled={readonly || pickedDir}
                placeholder={pickedDir ? '当前选中的是文件夹' : undefined}
                rows={18}
              />
            </div>
          </div>
        </Card>
      ) : (
        <Card>
          <Empty description="技能不存在，请返回列表。" />
        </Card>
      )}
      <Modal
        title="新建"
        open={newOpen}
        okText="确定"
        onCancel={() => setNewOpen(false)}
        onOk={() => void confirmNew()}
      >
        <Form layout="vertical">
          <Form.Item label="类型">
            <Radio.Group
              value={newKind}
              onChange={(e) => setNewKind(e.target.value)}
            >
              <Radio value="file">文件</Radio>
              <Radio value="dir">文件夹</Radio>
            </Radio.Group>
          </Form.Item>
          <Form.Item
            label={newKind === 'dir' ? '文件夹路径' : '文件路径'}
            extra={
              newKind === 'dir'
                ? '每一段只能包含字母、数字、点、下划线和中划线，不能以点开头。scripts 下不能再嵌套文件夹。'
                : '例如 references/input.md 或 scripts/check.js'
            }
          >
            <Input
              value={newPath}
              onChange={(e) => setNewPath(e.target.value)}
              placeholder={newKind === 'dir' ? 'references' : 'references/input.md'}
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        title="版本历史"
        open={versionsOpen}
        onCancel={() => setVersionsOpen(false)}
        footer={null}
        width={720}
      >
        <Typography.Paragraph type="secondary">
          每次发版升一个版本并保留历史。查看是只读快照，还原只覆盖当前工作副本。
        </Typography.Paragraph>
        <Table
          rowKey="version"
          size="small"
          pagination={false}
          dataSource={versions}
          locale={{ emptyText: '还没有发布过版本' }}
          columns={[
            {
              title: '版本',
              dataIndex: 'version',
              width: 140,
              render: (value: number) => (
                <Space>
                  <span>v{value}</span>
                  {value === latestVersion ? <Tag color="blue">最新</Tag> : null}
                  {viewVersion === value ? <Tag>查看中</Tag> : null}
                </Space>
              ),
            },
            {
              title: '发布时间',
              dataIndex: 'createdAt',
              render: (value: string) => formatWhen(value),
            },
            {
              title: '操作',
              width: 180,
              render: (_, item) => (
                <Space>
                  <Button
                    type="link"
                    size="small"
                    onClick={() => {
                      setViewVersion(viewVersion === item.version ? null : item.version)
                      setVersionsOpen(false)
                    }}
                  >
                    {viewVersion === item.version ? '回到工作副本' : '查看'}
                  </Button>
                  <Popconfirm
                    title={`把工作副本还原为 v${item.version}？`}
                    description="只覆盖当前编辑内容，历史版本都会保留。"
                    onConfirm={() => void onRestore(item.version)}
                  >
                    <Button type="link" size="small">
                      还原
                    </Button>
                  </Popconfirm>
                </Space>
              ),
            },
          ]}
        />
      </Modal>
    </div>
  )
}

function parseSkillPath(raw: string, directory: boolean): { path: string } | { error: string } {
  let path = raw.trim().replace(/\\/g, '/')
  if (directory) {
    while (path.endsWith('/')) {
      path = path.slice(0, -1)
    }
  } else if (path.endsWith('/')) {
    return { error: '文件路径不能以 / 结尾' }
  }
  if (!path) {
    return { error: directory ? '请填写文件夹路径' : '请填写文件路径' }
  }
  if (path.startsWith('/') || path.includes(':') || path.includes('..')) {
    return { error: '请使用相对路径，不能包含 .. 或盘符' }
  }
  for (const part of path.split('/')) {
    if (!/^[A-Za-z0-9._-]+$/.test(part)) {
      return { error: '每一段只能包含字母、数字、点、下划线和中划线' }
    }
    if (part.startsWith('.')) {
      return { error: '每一段不能以点开头' }
    }
  }
  if (path === '.versions' || path.startsWith('.versions/')) {
    return { error: '不能使用 .versions' }
  }
  if (path === 'SKILL.md') {
    return { error: 'SKILL.md 已存在' }
  }
  if (path === 'scripts' && !directory) {
    return { error: 'scripts 是文件夹，不能建成文件' }
  }
  if (path.startsWith('scripts/') && (directory || path.slice('scripts/'.length).includes('/'))) {
    return { error: 'scripts 目录下只能放脚本文件，不能再建子文件夹' }
  }
  return { path }
}

function pathExists(entries: string[], path: string, directory: boolean) {
  return entries.some((entry) => {
    const current = entry.endsWith('/') ? entry.slice(0, -1) : entry
    if (current === path) {
      return true
    }
    return directory && current.startsWith(`${path}/`)
  })
}

function entryMatches(entry: string, target: string) {
  const path = entry.endsWith('/') ? entry.slice(0, -1) : entry
  return path === target || path.startsWith(`${target}/`)
}

function rewriteEntry(entry: string, from: string, to: string) {
  const directory = entry.endsWith('/')
  const path = directory ? entry.slice(0, -1) : entry
  if (path === from) {
    return directory ? `${to}/` : to
  }
  if (path.startsWith(`${from}/`)) {
    const next = to + path.slice(from.length)
    return directory ? `${next}/` : next
  }
  return entry
}

function formatWhen(value: string) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString()
}

type SkillTreeNode = { name: string; path: string; dir: boolean; children: SkillTreeNode[] }

function buildSkillTree(entries: string[]): SkillTreeNode[] {
  const root: SkillTreeNode[] = []
  const dirs = new Map<string, SkillTreeNode>()

  function ensureDir(path: string): SkillTreeNode[] {
    if (!path) {
      return root
    }
    const existing = dirs.get(path)
    if (existing) {
      return existing.children
    }
    const parent = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''
    const name = path.includes('/') ? path.slice(path.lastIndexOf('/') + 1) : path
    const node: SkillTreeNode = { name, path, dir: true, children: [] }
    dirs.set(path, node)
    ensureDir(parent).push(node)
    return node.children
  }

  for (const entry of entries) {
    const isDir = entry.endsWith('/')
    const path = isDir ? entry.slice(0, -1) : entry
    if (isDir) {
      ensureDir(path)
      continue
    }
    const parent = path.includes('/') ? path.slice(0, path.lastIndexOf('/')) : ''
    const name = path.includes('/') ? path.slice(path.lastIndexOf('/') + 1) : path
    ensureDir(parent).push({ name, path, dir: false, children: [] })
  }

  function sortNodes(nodes: SkillTreeNode[]) {
    nodes.sort((a, b) => {
      if (a.dir !== b.dir) {
        return a.dir ? -1 : 1
      }
      return a.name.localeCompare(b.name)
    })
    nodes.forEach((node) => sortNodes(node.children))
  }
  sortNodes(root)
  return root
}

function renderSkillTree(
  nodes: SkillTreeNode[],
  options: {
    selectedPath: string
    selectedDir: string
    collapsed: Record<string, boolean>
    onToggle: (path: string) => void
    onSelect: (path: string) => void
  },
  depth = 0,
): ReactNode {
  return nodes.map((node) => {
    const collapsed = Boolean(options.collapsed[node.path])
    return (
      <div key={node.path}>
        <button
          type="button"
          className={
            node.dir
              ? options.selectedDir === node.path
                ? 'tree-item dir active'
                : 'tree-item dir'
              : options.selectedPath === node.path
                ? 'tree-item file active'
                : 'tree-item file'
          }
          style={{ paddingLeft: 8 + depth * 16 }}
          onClick={() => {
            if (node.dir) {
              options.onToggle(node.path)
            } else {
              options.onSelect(node.path)
            }
          }}
        >
          {node.dir ? `${collapsed ? '▸' : '▾'} ${node.name}` : node.name}
        </button>
        {node.dir && !collapsed ? renderSkillTree(node.children, options, depth + 1) : null}
      </div>
    )
  })
}
