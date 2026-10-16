<script setup>
import { computed, onMounted, ref } from 'vue'

const health = ref(null)
const connectionState = ref('loading')
const projects = ref([])
const projectsState = ref('loading')
const selectedProjectId = ref('')
const showRegisterForm = ref(false)
const submitting = ref(false)
const formError = ref('')
const listError = ref('')
const form = ref({ name: '', rootPath: '' })

const selectedProject = computed(() =>
  projects.value.find((project) => project.id === selectedProjectId.value) ?? null,
)

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

function formatDate(value) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}

onMounted(() => {
  loadHealth()
  loadProjects()
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
        <button class="nav-item" type="button" disabled title="后续里程碑开放">
          <span class="nav-icon">⌂</span>首页总览
        </button>
        <button class="nav-item" type="button" disabled title="后续里程碑开放">
          <span class="nav-icon">▤</span>环境管理
        </button>
        <button class="nav-item active" type="button" aria-current="page">
          <span class="nav-icon">▱</span>项目管理
        </button>
        <button class="nav-item" type="button" disabled title="Agent 将在后续里程碑接入">
          <span class="nav-icon">▤</span>Agent 对话<span class="nav-soon">后续</span>
        </button>
        <button class="nav-item" type="button" disabled title="后续里程碑开放">
          <span class="nav-icon">☑</span>任务执行
        </button>
      </nav>

      <div class="sidebar-bottom">
        <div class="nav-label">系统</div>
        <button class="nav-item" type="button" disabled title="后续里程碑开放">
          <span class="nav-icon">⚙</span>偏好设置
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
        <div class="breadcrumb"><strong>我的工作空间</strong><span>/</span><span>项目管理</span></div>
        <div class="topbar-right">
          <span class="local-badge">⌘ <span>本机开发环境</span></span>
          <span class="connection-badge" :class="`connection-${connectionState}`"><i></i>{{ connectionState === 'ready' ? '后端已连接' : connectionState === 'loading' ? '正在连接' : '后端未连接' }}</span>
          <span class="avatar small-avatar">D</span>
        </div>
      </header>

      <section class="content">
        <div class="page-heading">
          <div>
            <p class="eyebrow">PROJECT WORKSPACE</p>
            <h1>项目管理</h1>
            <p class="page-subtitle">登记本地项目，后续再识别技术栈、匹配环境并完成部署。</p>
          </div>
          <button class="primary-button" type="button" @click="showRegisterForm = true">
            <span>＋</span> 登记本地项目
          </button>
        </div>

        <div class="summary-grid">
          <article class="summary-card">
            <div class="summary-top"><span>已登记项目</span><span class="summary-icon blue">▣</span></div>
            <strong>{{ projectsState === 'ready' ? projects.length : '—' }}</strong>
            <small>数据来自本地 SQLite</small>
          </article>
          <article class="summary-card">
            <div class="summary-top"><span>等待识别</span><span class="summary-icon amber">⌕</span></div>
            <strong>{{ projects.filter((project) => project.projectType === 'UNKNOWN').length }}</strong>
            <small>项目扫描将在后续切片接入</small>
          </article>
          <article class="summary-card">
            <div class="summary-top"><span>后端连接</span><span class="summary-icon green">♡</span></div>
            <strong class="summary-word">{{ connectionState === 'ready' ? '正常' : connectionState === 'error' ? '异常' : '检测中' }}</strong>
            <small>{{ health ? `Spring Boot · Java ${health.javaVersion}` : '健康检查 API' }}</small>
          </article>
        </div>

        <div class="workspace-grid">
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
                <span class="project-row-copy"><strong>{{ project.name }}</strong><small>{{ project.rootPath }}</small><span class="tag-row"><i>本地项目</i><i>{{ project.projectType === 'UNKNOWN' ? '待识别' : project.projectType }}</i></span></span>
                <span class="project-state">已登记</span>
              </button>
            </div>
            <div class="list-note">当前只保存项目名称和路径，不会读取目录内容或运行项目。</div>
          </section>

          <section class="panel detail-panel">
            <template v-if="selectedProject">
              <div class="detail-heading">
                <div><p class="eyebrow">PROJECT DETAILS</p><h2>{{ selectedProject.name }}</h2><p>本地项目 · 登记于 {{ formatDate(selectedProject.createdAt) }}</p></div>
                <span class="project-state large-state">● 已登记</span>
              </div>

              <div class="info-grid">
                <div class="info-card"><span>项目来源</span><strong>{{ selectedProject.sourceType === 'LOCAL' ? '本地目录' : selectedProject.sourceType }}</strong></div>
                <div class="info-card"><span>技术栈识别</span><strong>{{ selectedProject.projectType === 'UNKNOWN' ? '尚未识别' : selectedProject.projectType }}</strong></div>
                <div class="info-card wide-info"><span>本地项目路径</span><strong class="path-value">{{ selectedProject.rootPath }}</strong></div>
                <div class="info-card wide-info"><span>登记 ID</span><strong class="id-value">{{ selectedProject.id }}</strong></div>
              </div>

              <div class="workflow-heading"><h3>后续工作流</h3><span>当前切片：项目登记</span></div>
              <ol class="workflow-list">
                <li class="complete"><span>✓</span><div><strong>登记项目</strong><small>已写入 SQLite</small></div></li>
                <li><span>2</span><div><strong>识别项目结构</strong><small>等待 M2</small></div></li>
                <li><span>3</span><div><strong>检查开发环境</strong><small>等待 M2</small></div></li>
                <li><span>4</span><div><strong>构建与启动</strong><small>等待后续任务执行模块</small></div></li>
              </ol>
              <div class="detail-callout"><span class="callout-mark">i</span><p>当前只完成项目登记。结构识别、环境检查和部署尚未接入，界面不会显示虚构的运行状态。</p></div>
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
