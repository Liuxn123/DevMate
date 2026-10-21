<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps({ projects: { type: Array, default: () => [] }, projectId: { type: String, default: '' }, pomPath: { type: String, default: '' } })
const tasks = ref([])
const selectedId = ref('')
const selected = computed(() => tasks.value.find(task => task.id === selectedId.value))
const project = ref(props.projectId)
const poms = ref([])
const pom = ref(props.pomPath)
const environment = ref({ javaHome: '', mavenHome: '', cachePath: '' })
const action = ref('MAVEN_PACKAGE')
const goal = ref('构建所选 Maven 项目，查看日志与退出结果')
const jarPath = ref('')
const useModel = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const logs = ref([])
const labels = { PENDING_APPROVAL: '待审批', APPROVED: '已审批', REJECTED: '已拒绝', REVOKED: '已撤回', RUNNING: '运行中', CANCELLING: '取消中', CANCELLED: '已取消', SUCCEEDED: '已成功退出', FAILED: '失败', TIMED_OUT: '超时', INTERRUPTED: '已中断' }
let timer
let refreshing = false
let contextSequence = 0

async function api(url, options = {}) {
  const response = await fetch(url, options)
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(data.message || `HTTP ${response.status}`)
  return data
}
const post = (url, body) => api(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, ...(body ? { body: JSON.stringify(body) } : {}) })

async function context() {
  const sequence = ++contextSequence
  poms.value = []
  pom.value = ''
  environment.value = { javaHome: '', mavenHome: '', cachePath: '' }
  if (!project.value) return
  try {
    const [paths, config] = await Promise.all([api(`/api/v1/projects/${project.value}/pom-candidates`), api(`/api/v1/projects/${project.value}/environment`)])
    if (sequence !== contextSequence) return
    poms.value = paths.pomPaths
    pom.value = paths.pomPaths.includes(props.pomPath) ? props.pomPath : paths.pomPaths[0] || ''
    environment.value = config
    if (paths.notice) notice.value = paths.notice
  } catch (cause) { if (sequence === contextSequence) error.value = cause.message }
}

async function refresh() {
  if (refreshing) return
  refreshing = true
  try {
    tasks.value = await api('/api/v1/tasks')
    if (!selectedId.value && tasks.value.length) selectedId.value = tasks.value[0].id
    const id = selectedId.value
    if (id) {
      const after = logs.value.at(-1)?.sequence || 0
      const batch = await api(`/api/v1/tasks/${id}/logs?after=${after}`)
      if (selectedId.value === id) logs.value.push(...batch)
    }
  } catch (cause) { error.value = cause.message }
  finally { refreshing = false }
}

async function perform(work) {
  busy.value = true
  error.value = ''
  notice.value = ''
  try { await work(); await refresh() }
  catch (cause) { error.value = cause.message }
  finally { busy.value = false }
}

async function saveEnvironment() {
  await perform(async () => {
    environment.value = await api(`/api/v1/projects/${project.value}/environment`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(environment.value) })
    notice.value = '项目环境已保存到 DevMate 数据库。'
  })
}

async function propose() {
  await perform(async () => {
    const task = await post('/api/v1/tasks', { projectId: project.value, pomPath: pom.value, goal: goal.value, action: action.value, jarPath: jarPath.value || null, useModel: useModel.value })
    selectedId.value = task.id
    notice.value = '计划已生成。查看命令和风险后审批；审批后需另行点击执行。'
  })
}

async function change(operation) {
  const task = selected.value
  await perform(() => post(`/api/v1/tasks/${task.id}/${operation}`, operation === 'approve' ? { version: task.version, contentHash: task.contentHash } : undefined))
}

async function retry() {
  const plan = selected.value.plan
  await perform(async () => {
    const task = await post('/api/v1/tasks', { projectId: plan.projectId, pomPath: plan.pomPath, goal: plan.goal, action: plan.action, jarPath: plan.action === 'JAVA_JAR' ? plan.command.at(-1).slice(plan.workingDirectory.length + 1) : null, useModel: false })
    selectedId.value = task.id
    notice.value = '已创建新的计划，需要重新审批。'
  })
}

function exportLogs() {
  const blob = new Blob([logs.value.map(line => line.content).join('')], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `devmate-${selectedId.value}.log`
  link.click()
  URL.revokeObjectURL(url)
}

watch(project, context)
watch(selectedId, () => { logs.value = [] })
onMounted(() => { context(); refresh(); timer = setInterval(refresh, 2000) })
onUnmounted(() => { clearInterval(timer); contextSequence++ })
</script>

<template>
  <div class="task-workspace">
    <p v-if="error" class="scan-error" role="alert">{{ error }}</p>
    <p v-if="notice" class="settings-success" role="status">{{ notice }}</p>
    <section class="panel task-planner">
      <div class="panel-heading"><div><h2>准备执行计划</h2><p>选择项目与环境；所有命令均由后端固定模板生成。</p></div></div>
      <div class="task-form-grid">
        <label>项目<select v-model="project" :disabled="busy"><option value="">请选择项目</option><option v-for="item in projects" :key="item.id" :value="item.id">{{ item.name }}</option></select></label>
        <label>Maven 配置<select v-model="pom" :disabled="busy"><option value="">请选择 pom.xml</option><option v-for="path in poms" :key="path" :value="path">{{ path }}</option></select></label>
      </div>
      <form class="task-form-grid" @submit.prevent="saveEnvironment">
        <label>JDK 根目录<input v-model="environment.javaHome" required placeholder="E:\Java\jdk-21" :disabled="busy" /></label>
        <label>Maven 根目录<input v-model="environment.mavenHome" required placeholder="E:\Tools\apache-maven-3.9.4" :disabled="busy" /></label>
        <label>依赖缓存绝对目录<input v-model="environment.cachePath" required placeholder="E:\DevMate\cache\maven" :disabled="busy" /></label>
        <div class="task-form-action"><button class="secondary-button" :disabled="busy || !project">保存项目环境</button></div>
      </form>
      <form class="task-form-grid" @submit.prevent="propose">
        <label>目标<input v-model="goal" required maxlength="2000" :disabled="busy" /></label>
        <label>动作<select v-model="action" :disabled="busy || useModel"><option value="MAVEN_PACKAGE">Maven package（含测试）</option><option value="MAVEN_VERIFY">Maven verify</option><option value="JAVA_JAR">启动现有 JAR</option></select></label>
        <label v-if="action === 'JAVA_JAR' || useModel">JAR 路径（相对于所选模块）<input v-model="jarPath" placeholder="target/demo-1.0.jar" :disabled="busy" /></label>
        <label class="task-checkbox"><input v-model="useModel" type="checkbox" :disabled="busy" />让 Agent 选择动作（目标会发送给已配置的模型服务）</label>
        <div class="task-form-action"><button class="primary-button" :disabled="busy || !project || !pom">{{ busy ? '处理中…' : '生成待审批计划' }}</button></div>
      </form>
    </section>

    <div class="workspace-grid task-columns">
      <section class="panel">
        <div class="panel-heading"><div><h2>任务记录</h2><p>最近 200 条；执行状态每 2 秒刷新。</p></div><button class="text-button" @click="refresh">刷新</button></div>
        <p v-if="!tasks.length" class="scan-placeholder">暂无任务，先生成一份计划。</p>
        <button v-for="task in tasks" :key="task.id" class="task-row" :class="{ selected: selectedId === task.id }" @click="selectedId = task.id">
          <strong>{{ task.plan.goal }}</strong><span>{{ labels[task.status] || task.status }} · {{ task.plan.action }}</span><small>{{ task.createdAt }}</small>
        </button>
      </section>
      <section class="panel task-detail">
        <template v-if="selected">
          <div class="panel-heading"><div><h2>{{ labels[selected.status] }}</h2><p>{{ selected.plan.goal }}</p></div></div>
          <dl class="task-facts"><dt>工作目录</dt><dd>{{ selected.plan.workingDirectory }}</dd><dt>版本 / SHA-256</dt><dd>{{ selected.version }} / {{ selected.contentHash }}</dd><dt>审批时间</dt><dd>{{ selected.approvedAt || '尚未审批' }}</dd><dt>时间上限</dt><dd>{{ selected.plan.timeoutSeconds }} 秒</dd></dl>
          <strong>执行参数（每行一个参数）</strong><pre class="task-log command-preview">{{ selected.plan.command.join('\n') }}</pre>
          <strong>变更与风险</strong><ul class="inspection-list notices"><li v-for="risk in selected.plan.risks" :key="risk">{{ risk }}</li></ul>
          <strong>验证标准</strong><ul class="inspection-list"><li v-for="item in selected.plan.verification" :key="item">{{ item }}</li></ul>
          <details><summary>审批快照与文件摘要</summary><pre class="task-log">{{ JSON.stringify(selected.plan, null, 2) }}</pre></details>
          <div class="settings-actions task-controls">
            <template v-if="selected.status === 'PENDING_APPROVAL'"><button class="secondary-button" :disabled="busy" @click="change('reject')">拒绝计划</button><button class="primary-button" :disabled="busy" @click="change('approve')">批准此版本与以上风险</button></template>
            <template v-if="selected.status === 'APPROVED'"><button class="secondary-button" :disabled="busy" @click="change('revoke')">撤回审批</button><button class="primary-button" :disabled="busy" @click="change('execute')">执行已审批计划</button></template>
            <button v-if="selected.status === 'RUNNING'" class="secondary-button" :disabled="busy" @click="change('cancel')">停止任务及子进程</button>
            <button v-if="['FAILED', 'TIMED_OUT', 'INTERRUPTED', 'CANCELLED', 'REJECTED', 'REVOKED', 'SUCCEEDED'].includes(selected.status)" class="secondary-button" :disabled="busy" @click="retry">重新生成计划并审批</button>
          </div>
          <p v-if="selected.result" class="scan-footnote">{{ selected.result }}</p>
          <div class="panel-heading"><h3>执行日志</h3><button class="text-button" @click="exportLogs">导出当前已加载日志</button></div>
          <pre class="task-log" aria-live="polite">{{ logs.map(line => line.content).join('') || '尚无执行日志' }}</pre>
        </template>
        <p v-else class="scan-placeholder">选择任务查看审批快照和日志。</p>
      </section>
    </div>
  </div>
</template>
