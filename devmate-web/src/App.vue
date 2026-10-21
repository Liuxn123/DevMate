<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import TaskWorkspace from './components/TaskWorkspace.vue'

const health = ref(null)
const connectionState = ref('loading')
const activeView = ref('projects')
const projects = ref([])
const projectsState = ref('loading')
const selectedProjectId = ref('')
const showRegisterForm = ref(false)
const submitting = ref(false)
const formError = ref('')
const listError = ref('')
const form = ref({ name: '', rootPath: '' })
const environment = ref(null)
const environmentState = ref('idle')
const environmentError = ref('')
const inspection = ref(null)
const inspectionState = ref('idle')
const inspectionError = ref('')
const pomCandidates = ref([])
const pomCandidatesState = ref('idle')
const pomCandidatesError = ref('')
const pomCandidatesTruncated = ref(false)
const selectedPomPath = ref('')
const agentStatus = ref(null)
const agentStatusError = ref('')
const agentSettings = ref({ baseUrl: 'https://api.deepseek.com', model: 'deepseek-chat', apiKey: '', keyConfigured: false, clearApiKey: false })
const availableModels = ref([])
const modelsState = ref('idle')
const settingsState = ref('loading')
const settingsMessage = ref('')
const settingsError = ref('')
const agentMessages = ref([])
const agentInput = ref('')
const agentState = ref('idle')
const agentError = ref('')
const conversations = ref([])
const conversationId = ref('')
const overviewTasks = ref([])
const viewTitle = computed(() => ({ overview: '首页总览', environment: '环境管理', projects: '项目管理', agent: 'Agent 对话', tasks: '任务执行', settings: '模型设置' })[activeView.value])

async function requestJson(url, options) {
  const response = await fetch(url, options)
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(data.message || `HTTP ${response.status}`)
  return data
}

async function loadConversations() {
  try { conversations.value = await requestJson('/api/v1/agent/conversations') }
  catch (error) { agentError.value = error.message }
}

async function openConversation() {
  if (agentState.value === 'loading') return
  agentError.value = ''
  agentMessages.value = []
  if (!conversationId.value) return
  agentState.value = 'loading'
  try {
    const messages = await requestJson(`/api/v1/agent/conversations/${conversationId.value}/messages`)
    agentMessages.value = messages.map(item => item.role === 'user'
      ? { role: 'user', content: item.payload } : { role: 'assistant', ...JSON.parse(item.payload) })
  } catch (error) { agentError.value = error.message }
  finally { agentState.value = 'ready' }
}

function newConversation() {
  if (agentState.value === 'loading') return
  conversationId.value = ''
  agentMessages.value = []
  agentError.value = ''
}

async function loadOverviewTasks() {
  try { overviewTasks.value = await requestJson('/api/v1/tasks') }
  catch { overviewTasks.value = null }
}

watch(activeView, value => { if (value === 'overview') loadOverviewTasks() })

const selectedProject = computed(() =>
  projects.value.find((project) => project.id === selectedProjectId.value) ?? null,
)

watch(selectedProjectId, () => {
  inspection.value = null
  inspectionState.value = 'idle'
  inspectionError.value = ''
  pomCandidates.value = []
  pomCandidatesState.value = 'idle'
  pomCandidatesError.value = ''
  pomCandidatesTruncated.value = false
  selectedPomPath.value = ''
})

async function loadHealth() {
  connectionState.value = 'loading'
  try {
    const response = await fetch('/api/health')
    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    health.value = await response.json()
    connectionState.value = 'ready'
  } catch {
    health.value = null
    connectionState.value = 'error'
  }
}

async function loadProjects() {
  projectsState.value = 'loading'
  listError.value = ''
  try {
    const response = await fetch('/api/v1/projects')
    const payload = await response.json().catch(() => [])
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    projects.value = payload
    projectsState.value = 'ready'
    if (!projects.value.some((project) => project.id === selectedProjectId.value)) {
      selectedProjectId.value = projects.value[0]?.id ?? ''
    }
  } catch (error) {
    projectsState.value = 'error'
    listError.value = error instanceof Error ? error.message : '项目列表加载失败'
  }
}

async function registerProject() {
  submitting.value = true
  formError.value = ''
  try {
    const response = await fetch('/api/v1/projects', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(form.value),
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)

    projects.value = [payload, ...projects.value]
    selectedProjectId.value = payload.id
    showRegisterForm.value = false
    form.value = { name: '', rootPath: '' }
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '登记失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function inspectSelectedProject() {
  if (!selectedProject.value || !selectedPomPath.value) return
  inspectionState.value = 'loading'
  inspectionError.value = ''
  try {
    const response = await fetch(`/api/v1/projects/${encodeURIComponent(selectedProject.value.id)}/inspect`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pomPath: selectedPomPath.value }),
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    inspection.value = payload
    inspectionState.value = 'ready'
    projects.value = projects.value.map((project) =>
      project.id === payload.projectId ? { ...project, projectType: payload.projectType } : project,
    )
  } catch (error) {
    inspectionState.value = 'error'
    inspectionError.value = error instanceof Error ? error.message : '项目识别失败'
  }
}

async function findPomCandidates() {
  if (!selectedProject.value) return
  pomCandidatesState.value = 'loading'
  pomCandidatesError.value = ''
  selectedPomPath.value = ''
  inspection.value = null
  inspectionState.value = 'idle'
  try {
    const response = await fetch(`/api/v1/projects/${encodeURIComponent(selectedProject.value.id)}/pom-candidates`)
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    pomCandidates.value = payload.pomPaths ?? []
    pomCandidatesTruncated.value = Boolean(payload.truncated)
    if (payload.notice) pomCandidatesError.value = payload.notice
    pomCandidatesState.value = 'ready'
  } catch (error) {
    pomCandidatesState.value = 'error'
    pomCandidatesError.value = error instanceof Error ? error.message : '查找 pom.xml 失败'
  }
}

async function scanEnvironment() {
  environmentState.value = 'loading'
  environmentError.value = ''
  try {
    const response = await fetch('/api/v1/environment/scan', { method: 'POST' })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    environment.value = payload
    environmentState.value = 'ready'
  } catch (error) {
    environmentState.value = 'error'
    environmentError.value = error instanceof Error ? error.message : '环境扫描失败'
  }
}

async function loadAgentStatus() {
  agentStatusError.value = ''
  try {
    const response = await fetch('/api/v1/agent/status')
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    agentStatus.value = payload
  } catch (error) {
    agentStatus.value = null
    agentStatusError.value = error instanceof Error ? error.message : 'Agent 状态读取失败'
  }
}

async function loadAgentSettings() {
  settingsState.value = 'loading'
  settingsError.value = ''
  try {
    const response = await fetch('/api/v1/agent/settings')
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    agentSettings.value = { ...agentSettings.value, ...payload, apiKey: '', clearApiKey: false }
    settingsState.value = 'ready'
  } catch (error) {
    settingsState.value = 'error'
    settingsError.value = error instanceof Error ? error.message : '模型设置读取失败'
  }
}

async function saveAgentSettings() {
  settingsState.value = 'saving'
  settingsError.value = ''
  settingsMessage.value = ''
  try {
    const response = await fetch('/api/v1/agent/settings', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        baseUrl: agentSettings.value.baseUrl,
        model: agentSettings.value.model,
        apiKey: agentSettings.value.apiKey,
        clearApiKey: agentSettings.value.clearApiKey,
      }),
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    agentSettings.value = { ...agentSettings.value, ...payload, apiKey: '', clearApiKey: false }
    settingsMessage.value = '模型设置已保存到本机。'
    settingsState.value = 'ready'
    await loadAgentStatus()
  } catch (error) {
    settingsState.value = 'error'
    settingsError.value = error instanceof Error ? error.message : '模型设置保存失败'
  }
}

async function loadAvailableModels() {
  modelsState.value = 'loading'
  settingsError.value = ''
  try {
    const response = await fetch('/api/v1/agent/models', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ baseUrl: agentSettings.value.baseUrl, apiKey: agentSettings.value.apiKey }),
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    availableModels.value = payload
    if (!payload.length) throw new Error('接口没有返回可用模型；你仍可以手动输入模型名称。')
    modelsState.value = 'ready'
    settingsMessage.value = `已读取 ${payload.length} 个模型。`
  } catch (error) {
    modelsState.value = 'error'
    settingsError.value = error instanceof Error ? error.message : '模型列表读取失败'
  }
}

async function testAgentConnection() {
  settingsState.value = 'testing'
  settingsError.value = ''
  settingsMessage.value = ''
  try {
    const response = await fetch('/api/v1/agent/settings/test', { method: 'POST' })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    if (payload.success) settingsMessage.value = payload.message
    else settingsError.value = payload.message
    settingsState.value = 'ready'
  } catch (error) {
    settingsState.value = 'error'
    settingsError.value = error instanceof Error ? error.message : '连接测试失败'
  }
}

async function sendAgentMessage() {
  const message = agentInput.value.trim()
  if (!message || agentState.value === 'loading') return
  agentMessages.value.push({ role: 'user', content: message })
  agentInput.value = ''
  agentState.value = 'loading'
  agentError.value = ''
  try {
    if (!conversationId.value) {
      const session = await requestJson('/api/v1/agent/conversations', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ projectId: selectedProjectId.value || null, pomPath: selectedPomPath.value || null }),
      })
      conversationId.value = session.id
      await loadConversations()
    }
    const response = await fetch(`/api/v1/agent/conversations/${conversationId.value}/messages`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        message,
        projectId: selectedProjectId.value || null,
        pomPath: selectedPomPath.value || null,
      }),
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(payload.message || `HTTP ${response.status}`)
    agentMessages.value.push({ role: 'assistant', ...payload })
    agentState.value = 'ready'
  } catch (error) {
    agentError.value = error instanceof Error ? error.message : 'Agent 请求失败'
    agentState.value = 'error'
  }
}

function formatEventDetails(details) {
  return JSON.stringify(details, null, 2)
}

function projectTypeLabel(projectType) {
  return ({
    SPRING_BOOT_MAVEN: 'Spring Boot · Maven',
    MAVEN_JAVA: 'Java · Maven',
    UNKNOWN: '待识别',
  })[projectType] ?? projectType
}

async function scrollToEnvironment() {
  activeView.value = 'environment'
  await nextTick()
  document.getElementById('environment-panel')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function formatDate(value) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

onMounted(() => {
  loadHealth()
  loadProjects()
  loadAgentStatus()
  loadAgentSettings()
  loadConversations()
  loadOverviewTasks()
})
</script>

<template>
  <div class="app-frame">
    <aside class="sidebar">
      <a class="brand" href="#top" aria-label="DevMate 项目管理">
        <span class="brand-mark">D</span>
        <span class="brand-copy"><strong>DevMate</strong><small>LOCAL DEV AGENT</small></span>
      </a>

      <div class="nav-label">工作空间</div>
      <nav class="main-nav" aria-label="主导航">
        <button class="nav-item" :class="{ active: activeView === 'overview' }" type="button" @click="activeView = 'overview'">
          <span class="nav-icon">⌂</span>首页总览
        </button>
        <button class="nav-item" :class="{ active: activeView === 'environment' }" type="button" @click="scrollToEnvironment">
          <span class="nav-icon">▤</span>环境管理
        </button>
        <button class="nav-item" :class="{ active: activeView === 'projects' }" type="button" :aria-current="activeView === 'projects' ? 'page' : undefined" @click="activeView = 'projects'">
          <span class="nav-icon">▱</span>项目管理
        </button>
        <button class="nav-item" :class="{ active: activeView === 'agent' }" type="button" :aria-current="activeView === 'agent' ? 'page' : undefined" @click="activeView = 'agent'">
          <span class="nav-icon">▤</span>Agent 对话
        </button>
        <button class="nav-item" :class="{ active: activeView === 'tasks' }" type="button" @click="activeView = 'tasks'">
          <span class="nav-icon">☑</span>任务执行
        </button>
      </nav>

      <div class="sidebar-bottom">
        <div class="nav-label">系统</div>
        <button class="nav-item" :class="{ active: activeView === 'settings' }" type="button" :aria-current="activeView === 'settings' ? 'page' : undefined" @click="activeView = 'settings'">
          <span class="nav-icon">⚙</span>模型设置
        </button>
        <div class="agent-status" :class="`connection-${connectionState}`">
          <span class="status-line"><i></i><strong>本地后端服务</strong><b>{{ connectionState === 'ready' ? '正常' : connectionState === 'loading' ? '连接中' : '未连接' }}</b></span>
          <small>{{ health ? `Spring Boot · Java ${health.javaVersion}` : '127.0.0.1:8080' }}</small>
        </div>
        <div class="profile"><span class="avatar">D</span><span><strong>本地开发者</strong><small>个人工作空间</small></span></div>
      </div>
    </aside>

    <main id="top" class="main-area">
      <header class="topbar">
        <div class="breadcrumb"><strong>我的工作空间</strong><span>/</span><span>{{ viewTitle }}</span></div>
        <div class="topbar-right">
          <span class="local-badge">⌘ <span>本机开发环境</span></span>
          <span class="connection-badge" :class="`connection-${connectionState}`"><i></i>{{ connectionState === 'ready' ? '后端已连接' : connectionState === 'loading' ? '正在连接' : '后端未连接' }}</span>
          <span class="avatar small-avatar">D</span>
        </div>
      </header>

      <section class="content">
        <div class="page-heading">
          <div>
            <p class="eyebrow">{{ activeView === 'agent' ? 'AI ASSISTANT' : activeView === 'settings' ? 'WORKSPACE SETTINGS' : 'PROJECT WORKSPACE' }}</p>
            <h1>{{ viewTitle }}</h1>
            <p class="page-subtitle">{{ activeView === 'agent' ? 'Agent 可按需调用项目识别和本机环境扫描两个只读工具。' : activeView === 'settings' ? '配置兼容 OpenAI API 的模型服务地址、模型和 API Key。' : activeView === 'tasks' ? '先审阅计划和风险，批准具体版本后执行，并查看状态与日志。' : activeView === 'overview' ? '查看真实项目与最近任务状态。' : '登记后可只读识别 Maven 项目，并查看本机 JDK、Maven、Git 版本与路径。' }}</p>
          </div>
          <button v-if="activeView === 'projects'" class="primary-button" type="button" @click="showRegisterForm = true">
            <span>＋</span> 登记本地项目
          </button>
        </div>

        <section v-if="activeView === 'overview'" class="panel task-overview">
          <div class="panel-heading"><div><h2>工作空间状态</h2><p>项目与任务数据来自本机数据库。</p></div><button class="text-button" @click="loadOverviewTasks">刷新任务</button></div>
          <div class="summary-grid">
            <article class="summary-card"><span>登记项目</span><strong>{{ projectsState === 'ready' ? projects.length : '—' }}</strong></article>
            <article class="summary-card"><span>运行任务</span><strong>{{ overviewTasks ? overviewTasks.filter(task => ['RUNNING', 'CANCELLING'].includes(task.status)).length : '—' }}</strong></article>
            <article class="summary-card"><span>待审批</span><strong>{{ overviewTasks ? overviewTasks.filter(task => task.status === 'PENDING_APPROVAL').length : '—' }}</strong></article>
          </div>
          <p v-if="overviewTasks === null" class="scan-error">任务数据加载失败，点击刷新重试。</p>
          <p v-else-if="!overviewTasks.length" class="scan-placeholder">暂无任务，前往任务执行页生成计划。</p>
          <button v-for="task in (overviewTasks || []).slice(0, 5)" :key="task.id" class="task-row" @click="activeView = 'tasks'"><strong>{{ task.plan.goal }}</strong><span>{{ task.status }} · {{ task.createdAt }}</span></button>
        </section>

        <TaskWorkspace v-if="activeView === 'tasks'" :projects="projects" :project-id="selectedProjectId" :pom-path="selectedPomPath" />

        <section v-if="activeView === 'agent'" class="agent-workspace panel">
          <div class="conversation-toolbar">
            <label>历史会话<select v-model="conversationId" :disabled="agentState === 'loading'" @change="openConversation"><option value="">新会话</option><option v-for="session in conversations" :key="session.id" :value="session.id">{{ session.title }} · {{ formatDate(session.createdAt) }}</option></select></label>
            <button class="secondary-button" :disabled="agentState === 'loading'" @click="newConversation">新建会话</button>
            <small v-if="conversationId">会话使用创建时选定的项目和 pom；切换上下文请新建会话。</small>
          </div>
          <p v-if="conversationId" class="scan-footnote">会话项目：{{ projects.find(item => item.id === conversations.find(session => session.id === conversationId)?.projectId)?.name || '未关联项目' }}；Maven 配置：{{ conversations.find(session => session.id === conversationId)?.pomPath || '未选择' }}</p>
          <div v-if="!conversationId" class="agent-context-bar">
            <div class="agent-context-fields">
              <label>关联项目
                <select v-model="selectedProjectId" :disabled="!!conversationId || agentState === 'loading'">
                  <option value="">不关联项目</option>
                  <option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option>
                </select>
              </label>
              <button v-if="selectedProject" class="secondary-button" type="button" :disabled="!!conversationId || pomCandidatesState === 'loading'" @click="findPomCandidates">
                {{ pomCandidatesState === 'loading' ? '查找中…' : '选择识别用 pom.xml' }}
              </button>
              <label v-if="pomCandidates.length" class="agent-pom-select">Maven 配置
                <select v-model="selectedPomPath" :disabled="!!conversationId || agentState === 'loading'">
                  <option value="">不启用项目识别</option>
                  <option v-for="path in pomCandidates" :key="path" :value="path">{{ path }}</option>
                </select>
              </label>
            </div>
            <span class="agent-model-state" :class="agentStatus?.configured ? 'is-ready' : 'is-unconfigured'">
              {{ agentStatus?.configured ? `模型已配置 · ${agentStatus.model}` : '模型未配置' }}
            </span>
          </div>

          <div v-if="agentStatusError" class="agent-notice error-state" role="alert">{{ agentStatusError }} <button class="text-button" type="button" @click="loadAgentStatus">重试</button></div>
          <div v-else-if="agentStatus && !agentStatus.configured" class="agent-notice">
            <strong>先配置模型，才能开始 Agent 对话</strong>
            <p>在模型设置中填写 API 地址和 API Key 并保存。未配置模型时，项目管理和环境扫描仍可单独使用。</p>
            <button class="text-button" type="button" @click="activeView = 'settings'">前往模型设置 →</button>
          </div>
          <div class="agent-safety-note">本阶段仅开放 <code>inspect_project</code> 与 <code>scan_environment</code>。最多调用 3 轮；不会运行命令、安装工具或修改文件。你发送的消息及 Agent 使用的项目/环境事实会提交到已配置的模型服务处理。</div>

          <div class="agent-transcript" aria-live="polite">
            <div v-if="!agentMessages.length" class="agent-empty">
              <span class="agent-avatar">D</span>
              <div><strong>DevMate Agent</strong><p>可以问我本机 JDK / Maven / Git 状态，或让 Agent 查看当前选中的 Maven 项目。</p></div>
            </div>
            <article v-for="(message, index) in agentMessages" :key="index" class="chat-message" :class="`message-${message.role}`">
              <strong>{{ message.role === 'user' ? '你' : 'DevMate Agent' }}</strong>
              <p>{{ message.role === 'user' ? message.content : message.answer }}</p>
              <div v-if="message.events?.length" class="agent-events">
                <strong>本次决策与工具记录 · {{ message.toolRounds }} 轮</strong>
                <details v-for="event in message.events" :key="event.sequence" class="agent-event">
                  <summary><span :class="`event-${event.type.toLowerCase()}`">{{ event.type }}</span><b v-if="event.toolName">{{ event.toolName }}</b>{{ event.summary }}</summary>
                  <pre v-if="Object.keys(event.details || {}).length">{{ formatEventDetails(event.details) }}</pre>
                </details>
              </div>
            </article>
            <div v-if="agentState === 'loading'" class="agent-loading">Agent 正在分析并按需调用只读工具…</div>
            <p v-if="agentError" class="scan-error" role="alert">{{ agentError }}</p>
          </div>

          <form class="agent-composer" @submit.prevent="sendAgentMessage">
            <textarea v-model="agentInput" maxlength="4000" rows="2" placeholder="例如：检查一下我本机的 JDK 和 Maven 版本" :disabled="!agentStatus?.configured || agentState === 'loading'"></textarea>
            <button class="primary-button" type="submit" :disabled="!agentStatus?.configured || agentState === 'loading' || !agentInput.trim()">{{ agentState === 'loading' ? '处理中…' : '发送 ↗' }}</button>
          </form>
          <p class="agent-footer">会话与工具轨迹保存在本机数据库；最近 6 轮对话会作为上下文发送到模型，每个会话最多 100 轮。不记录模型内部推理。</p>
        </section>

        <section v-if="activeView === 'settings'" class="settings-workspace panel">
          <div class="panel-heading">
            <div><h2>Agent 模型服务</h2><p>配置兼容 OpenAI API 的连接地址和本机凭据</p></div>
            <span class="agent-model-state" :class="agentSettings.keyConfigured ? 'is-ready' : 'is-unconfigured'">{{ agentSettings.keyConfigured ? 'API Key 已设置' : '尚未配置' }}</span>
          </div>
          <form class="settings-form" @submit.prevent="saveAgentSettings">
            <label>API 地址
              <input v-model="agentSettings.baseUrl" type="url" required placeholder="https://api.example.com/v1" />
              <small>填写 OpenAI 兼容 API 的基础地址，例如 https://api.deepseek.com 或 https://api.openai.com/v1。</small>
            </label>
            <label>模型名称
              <input v-model="agentSettings.model" list="agent-model-options" required placeholder="输入模型名称或先获取模型列表" />
              <datalist id="agent-model-options"><option v-for="model in availableModels" :key="model" :value="model" /></datalist>
              <button class="text-button" type="button" :disabled="modelsState === 'loading'" @click="loadAvailableModels">{{ modelsState === 'loading' ? '正在获取模型…' : '根据 API 地址和 Key 获取模型列表' }}</button>
            </label>
            <label>API Key
              <input v-model="agentSettings.apiKey" type="password" autocomplete="new-password" placeholder="输入 API Key；已有密钥时留空可保留" />
              <small>{{ agentSettings.keyConfigured ? '已有密钥不会回显。填写新密钥会替换；留空会保留现有密钥。' : '密钥只发送到本机 DevMate 后端，不会返回给页面。' }}</small>
            </label>
            <label v-if="agentSettings.keyConfigured" class="settings-clear-key"><input v-model="agentSettings.clearApiKey" type="checkbox" />移除后端当前保存的 API Key</label>
            <p class="settings-storage-note">{{ agentSettings.storageNote || '连接地址、模型和 API Key 会保存到本机数据库；API Key 不会返回给页面。' }}</p>
            <p v-if="settingsError" class="form-error" role="alert">{{ settingsError }}</p>
            <p v-if="settingsMessage" class="settings-success" role="status">{{ settingsMessage }}</p>
            <div class="settings-actions">
              <button class="secondary-button" type="button" :disabled="settingsState === 'loading' || settingsState === 'saving' || settingsState === 'testing' || !agentSettings.keyConfigured" @click="testAgentConnection">{{ settingsState === 'testing' ? '测试中…' : '测试连接' }}</button>
              <button class="primary-button" type="submit" :disabled="settingsState === 'loading' || settingsState === 'saving' || settingsState === 'testing'">{{ settingsState === 'saving' ? '保存中…' : '保存设置' }}</button>
            </div>
          </form>
        </section>

        <div v-if="activeView === 'projects'" class="summary-grid">
          <article class="summary-card">
            <div class="summary-top"><span>已登记项目</span><span class="summary-icon blue">▣</span></div>
            <strong>{{ projectsState === 'ready' ? projects.length : '—' }}</strong>
            <small>数据来自本地 SQLite</small>
          </article>
          <article class="summary-card">
            <div class="summary-top"><span>等待识别</span><span class="summary-icon amber">⌕</span></div>
            <strong>{{ projects.filter((project) => project.projectType === 'UNKNOWN').length }}</strong>
            <small>对登记项目执行只读识别</small>
          </article>
          <article class="summary-card">
            <div class="summary-top"><span>后端连接</span><span class="summary-icon green">♡</span></div>
            <strong class="summary-word">{{ connectionState === 'ready' ? '正常' : connectionState === 'error' ? '异常' : '检测中' }}</strong>
            <small>{{ health ? `Spring Boot · Java ${health.javaVersion}` : '健康检查 API' }}</small>
          </article>
        </div>

        <section v-if="activeView === 'projects' || activeView === 'environment'" id="environment-panel" class="panel environment-panel">
          <div class="panel-heading">
            <div><h2>本机开发工具</h2><p>只查询版本与安装位置，不修改系统环境</p></div>
            <button class="secondary-button" type="button" :disabled="environmentState === 'loading'" @click="scanEnvironment">
              {{ environmentState === 'loading' ? '扫描中…' : environmentState === 'ready' ? '重新扫描' : '扫描本机' }}
            </button>
          </div>
          <p v-if="environmentState === 'idle'" class="scan-placeholder">点击扫描，检查 JDK、Maven 和 Git 是否可用。</p>
          <p v-else-if="environmentState === 'loading'" class="scan-placeholder">正在执行固定版本查询，最长等待约 12 秒…</p>
          <div v-else-if="environmentState === 'error'" class="scan-error" role="alert">
            {{ environmentError }} <button class="text-button" type="button" @click="scanEnvironment">重试</button>
          </div>
          <template v-else-if="environment">
            <div class="tool-grid">
              <article v-for="tool in environment.tools" :key="tool.name" class="tool-card">
                <div class="tool-card-heading"><strong>{{ tool.name }}</strong><span class="tool-status" :class="`tool-${tool.status.toLowerCase()}`">{{ tool.status === 'AVAILABLE' ? '可用' : tool.status === 'NOT_FOUND' ? '未发现' : '查询失败' }}</span></div>
                <strong class="tool-version">{{ tool.version ?? '—' }}</strong>
                <small class="tool-path">{{ tool.path ?? tool.details }}</small>
                <small v-if="tool.path && tool.details" class="tool-detail">{{ tool.details }}</small>
              </article>
            </div>
            <p class="scan-footnote">扫描时间：{{ formatDate(environment.scannedAt) }}。{{ environment.safetyNote }}</p>
          </template>
        </section>

        <div v-if="activeView === 'projects'" class="workspace-grid">
          <section class="panel project-list-panel">
            <div class="panel-heading">
              <div><h2>本地项目 <span class="count">({{ projects.length }})</span></h2><p>选择一个项目查看登记信息</p></div>
              <button class="text-button" type="button" :disabled="projectsState === 'loading'" @click="loadProjects">↻ 刷新</button>
            </div>

            <div v-if="projectsState === 'loading'" class="state-card">正在读取本地项目…</div>
            <div v-else-if="projectsState === 'error'" class="state-card error-state">
              <strong>项目列表加载失败</strong><span>{{ listError }}</span>
              <button class="text-button" type="button" @click="loadProjects">重试</button>
            </div>
            <div v-else-if="projects.length === 0" class="empty-state">
              <span class="empty-icon">▱</span><strong>还没有登记项目</strong>
              <p>登记一个已有目录，先把项目放进工作空间。</p>
              <button class="secondary-button" type="button" @click="showRegisterForm = true">登记第一个项目</button>
            </div>
            <div v-else class="project-list">
              <button
                v-for="project in projects"
                :key="project.id"
                class="project-row"
                :class="{ selected: project.id === selectedProjectId }"
                type="button"
                @click="selectedProjectId = project.id"
              >
                <span class="project-avatar">{{ project.name.slice(0, 1).toUpperCase() }}</span>
                <span class="project-row-copy"><strong>{{ project.name }}</strong><small>{{ project.rootPath }}</small><span class="tag-row"><i>本地项目</i><i>{{ projectTypeLabel(project.projectType) }}</i></span></span>
                <span class="project-state">已登记</span>
              </button>
            </div>
            <div class="list-note">项目登记只保存名称和路径。需要时可手动触发只读识别；不会执行项目脚本或修改文件。</div>
          </section>

          <section class="panel detail-panel">
            <template v-if="selectedProject">
              <div class="detail-heading">
                <div><p class="eyebrow">PROJECT DETAILS</p><h2>{{ selectedProject.name }}</h2><p>本地项目 · 登记于 {{ formatDate(selectedProject.createdAt) }}</p></div>
                <div class="detail-actions"><button class="secondary-button" type="button" :disabled="pomCandidatesState === 'loading'" @click="findPomCandidates">{{ pomCandidatesState === 'loading' ? '查找中…' : '选择 pom.xml' }}</button><button class="primary-button" type="button" :disabled="inspectionState === 'loading' || !selectedPomPath" @click="inspectSelectedProject">{{ inspectionState === 'loading' ? '识别中…' : '只读识别项目' }}</button><span class="project-state large-state">● 已登记</span></div>
              </div>

              <div class="info-grid">
                <div class="info-card"><span>项目来源</span><strong>{{ selectedProject.sourceType === 'LOCAL' ? '本地目录' : selectedProject.sourceType }}</strong></div>
                <div class="info-card"><span>技术栈识别</span><strong>{{ projectTypeLabel(selectedProject.projectType) }}</strong></div>
                <div class="info-card wide-info"><span>本地项目路径</span><strong class="path-value">{{ selectedProject.rootPath }}</strong></div>
                <div class="info-card wide-info"><span>登记 ID</span><strong class="id-value">{{ selectedProject.id }}</strong></div>
              </div>

              <section class="pom-picker" aria-live="polite">
                <div class="pom-picker-copy"><strong>选择 Maven 配置</strong><small>在登记目录下最多查找 4 层；只列出 pom.xml，不执行 Maven。</small></div>
                <label v-if="pomCandidates.length" class="pom-select-label">要识别的 pom.xml
                  <select v-model="selectedPomPath">
                    <option value="" disabled>请选择一个 pom.xml</option>
                    <option v-for="path in pomCandidates" :key="path" :value="path">{{ path }}</option>
                  </select>
                </label>
                <p v-if="pomCandidatesState === 'idle'" class="pom-picker-note">点击“选择 pom.xml”查找候选文件，再由你选择具体模块。</p>
                <p v-else-if="pomCandidatesState === 'loading'" class="pom-picker-note">正在登记目录内有限深度查找 pom.xml…</p>
                <p v-else-if="pomCandidatesState === 'error' || (pomCandidatesState === 'ready' && !pomCandidates.length)" class="pom-picker-note pom-picker-error">{{ pomCandidatesError || '未找到 pom.xml。请确认所登记目录和项目结构。' }}</p>
                <p v-if="pomCandidatesTruncated" class="pom-picker-note">候选列表达到上限，只显示前 100 个 pom.xml。</p>
                <p v-if="pomCandidatesError && pomCandidates.length" class="pom-picker-note pom-picker-error">{{ pomCandidatesError }}</p>
              </section>

              <section v-if="inspection || inspectionState === 'error'" class="inspection-panel" aria-live="polite">
                <div class="inspection-heading"><div><h3>识别结果</h3><small v-if="inspection">{{ inspection.status === 'INSPECTED' ? '已完成' : '需要检查路径' }} · {{ formatDate(inspection.inspectedAt) }}</small></div><span v-if="inspection" class="inspection-type">{{ projectTypeLabel(inspection.projectType) }}</span></div>
                <p v-if="inspectionError" class="scan-error" role="alert">{{ inspectionError }}</p>
                <p v-if="inspection?.pomPath" class="inspection-pom-path">所选配置：{{ inspection.pomPath }}</p>
                <div v-if="inspection" class="inspection-facts">
                  <div><span>Java 版本要求</span><strong>{{ inspection.javaVersionRequirement || 'pom.xml 未声明' }}</strong></div>
                  <div><span>Maven Wrapper</span><strong>{{ inspection.mavenWrapper ? '已发现' : '未发现' }}</strong></div>
                </div>
                <ul v-if="inspection?.evidence?.length" class="inspection-list"><li v-for="item in inspection.evidence" :key="item">{{ item }}</li></ul>
                <ul v-if="inspection?.notices?.length" class="inspection-list notices"><li v-for="item in inspection.notices" :key="item">{{ item }}</li></ul>
              </section>

              <div class="workflow-heading"><h3>后续工作流</h3><span>当前切片：M2 只读识别与扫描</span></div>
              <ol class="workflow-list">
                <li class="complete"><span>✓</span><div><strong>登记项目</strong><small>已写入 SQLite</small></div></li>
                <li :class="{ complete: inspection?.status === 'INSPECTED' && inspection?.projectType !== 'UNKNOWN' }"><span>{{ inspection?.status === 'INSPECTED' && inspection?.projectType !== 'UNKNOWN' ? '✓' : '2' }}</span><div><strong>识别项目结构</strong><small>{{ inspection?.projectType && inspection.projectType !== 'UNKNOWN' ? `已识别：${projectTypeLabel(inspection.projectType)}` : '选择 pom.xml 后执行识别' }}</small></div></li>
                <li :class="{ complete: environmentState === 'ready' }"><span>{{ environmentState === 'ready' ? '✓' : '3' }}</span><div><strong>检查开发环境</strong><small>{{ environmentState === 'ready' ? '已扫描本机工具' : '可手动触发扫描' }}</small></div></li>
                <li><span>4</span><div><strong>构建与启动</strong><button class="text-button" type="button" @click="activeView = 'tasks'">配置环境并生成待审批计划 →</button></div></li>
              </ol>
              <div class="detail-callout"><span class="callout-mark">i</span><p>你选择的 pom.xml 必须位于已登记目录内；识别读取该 Maven 模块的配置和少量固定标记，不运行项目命令，也不修改系统环境或项目文件。</p></div>
            </template>
            <div v-else class="empty-detail"><span>▱</span><strong>选择一个项目</strong><p>登记信息和后续步骤会显示在这里。</p></div>
          </section>
        </div>
      </section>
    </main>

    <div v-if="showRegisterForm" class="modal-backdrop" @click.self="showRegisterForm = false">
      <section class="register-modal" role="dialog" aria-modal="true" aria-labelledby="register-title">
        <div class="modal-heading"><div><p class="eyebrow">ADD LOCAL PROJECT</p><h2 id="register-title">登记本地项目</h2></div><button class="close-button" type="button" aria-label="关闭" @click="showRegisterForm = false">×</button></div>
        <p class="modal-copy">只登记名称和目录路径。此操作不会扫描文件，也不会运行项目中的命令。</p>
        <form @submit.prevent="registerProject">
          <label>项目名称<input v-model="form.name" required maxlength="120" placeholder="例如：springboot-demo" /></label>
          <label>本地目录路径<input v-model="form.rootPath" required maxlength="1000" placeholder="例如：D:\Workspace\springboot-demo" /></label>
          <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
          <div class="modal-actions"><button class="secondary-button" type="button" :disabled="submitting" @click="showRegisterForm = false">取消</button><button class="primary-button" type="submit" :disabled="submitting">{{ submitting ? '正在登记…' : '确认登记' }}</button></div>
        </form>
      </section>
    </div>
  </div>
</template>
