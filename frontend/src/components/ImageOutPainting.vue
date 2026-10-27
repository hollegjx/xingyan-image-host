<template>
  <a-modal :open="open" title="AI 扩图" :footer="null" :width="820" :mask-closable="!busy" :closable="!busy" @cancel="close">
    <a-alert v-if="error" type="warning" :message="error" show-icon style="margin-bottom: 12px" />
    <a-alert v-if="!available && !loading" type="info" message="AI 扩图尚未启用：需要管理员配置百炼、存储及测试额度。" />
    <a-spin v-if="loading" />
    <div class="comparison">
      <section><h4>原始图片</h4><img :src="picture.url" alt="原始图片" /></section>
      <section><h4>扩图结果</h4><img v-if="preview" :src="preview" alt="扩图结果" /><p v-else>{{ statusLabel }}</p></section>
    </div>
    <p v-if="task">任务：{{ task.id }} · {{ statusLabel }}</p>
    <p v-if="task?.message">{{ task.message }}</p>
    <a-alert type="info" message="当前使用千问图像 3.0。倍数表示希望扩展的画面视野；输出最多约 100 万像素，可能缩小尺寸或改变原图细节。请先预览，再决定是否替换。" style="margin-bottom: 12px" />
    <a-space wrap>
      <label>水平倍数 <a-input-number v-model:value="xScale" :min="1" :max="3" :step="0.1" :disabled="locked" /></label>
      <label>垂直倍数 <a-input-number v-model:value="yScale" :min="1" :max="3" :step="0.1" :disabled="locked" /></label>
    </a-space>
    <p><a-checkbox v-model:checked="consent" :disabled="locked">我同意将这张图片交给阿里云百炼处理，生成任务可能消耗服务额度。</a-checkbox></p>
    <p v-if="picture.spaceId">私有图片仍受登录鉴权保护；仅经此次同意后向百炼提供短期读取授权。</p>
    <a-space wrap>
      <a-button type="primary" :disabled="!available || !consent || locked || loading" :loading="creating" @click="create">生成图片</a-button>
      <a-button v-if="task" :disabled="busy || checking" @click="refresh">刷新任务</a-button>
      <a-button v-if="task?.status === 'SUCCEEDED'" type="primary" :loading="applying" :disabled="!preview || busy" @click="apply">应用结果并替换</a-button>
    </a-space>
    <p>关闭窗口只停止本页轮询，不会取消云端任务。重新打开可恢复查询。超过 2 MB 的 AI 结果保存时会压缩为 JPEG。</p>
  </a-modal>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { message } from 'ant-design-vue'
import { aiCapabilities, latestAiTask, createAiTask, getAiTask, applyAiTask, editingBlob, taskPending, type AiTask } from '@/service/aiEditing'
const props = defineProps<{ picture: API.PictureVO; onSuccess?: (p: API.PictureVO) => void | Promise<void> }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
const open = ref(false), loading = ref(false), creating = ref(false), applying = ref(false), checking = ref(false)
const available = ref(false), consent = ref(false), error = ref(''), preview = ref('')
const task = ref<AiTask>(), xScale = ref<number | null>(2), yScale = ref<number | null>(2)
const busy = computed(() => creating.value || applying.value)
const locked = computed(() => busy.value || taskPending(task.value?.status) || ['UNKNOWN', 'APPLY_UNKNOWN', 'APPLYING'].includes(task.value?.status || ''))
const labels: Record<string, string> = { QUEUED: '等待后台处理', SUBMITTING: '正在生成（请勿重复提交）', PENDING: '排队中', RUNNING: '生成中', SUSPENDED: '等待恢复', SUCCEEDED: '已生成，尚未应用', FAILED: '生成失败', CANCELED: '已取消', UNKNOWN: '结果未确认', APPLYING: '正在保存', APPLY_UNKNOWN: '保存未确认', APPLIED: '已应用', EXPIRED: '结果已过期' }
const statusLabel = computed(() => task.value ? labels[task.value.status] || '未知状态' : '尚未生成')
let generation = 0, timer: ReturnType<typeof setTimeout> | undefined, abort: AbortController | undefined
let requestId = '', previewTask = '', deadline = 0
function cleanup() { generation++; clearTimeout(timer); abort?.abort(); if (preview.value) URL.revokeObjectURL(preview.value); preview.value = ''; previewTask = '' }
function close() { if (busy.value) return; open.value = false; cleanup() }
async function accept(value: AiTask | undefined, ticket: number) {
  if (ticket !== generation) return
  task.value = value
  if (value?.status === 'SUCCEEDED' && value.resultUrl && previewTask !== value.id) {
    abort?.abort(); abort = new AbortController()
    const blob = await editingBlob(value.resultUrl, abort.signal)
    if (ticket !== generation) return
    if (preview.value) URL.revokeObjectURL(preview.value)
    preview.value = URL.createObjectURL(blob); previewTask = value.id
  }
  if (value && taskPending(value.status) && Date.now() < deadline) timer = setTimeout(() => void refresh(), 3000)
  else if (value && taskPending(value.status)) error.value = '自动查询已暂停，请点击刷新任务；这不代表生成失败。'
}
async function openModal() {
  cleanup(); const ticket = generation
  open.value = true; loading.value = true; consent.value = false; error.value = ''; task.value = undefined; deadline = Date.now() + 10 * 60 * 1000
  try {
    const capabilities = await aiCapabilities()
    if (ticket !== generation) return
    if (capabilities.code !== 0 || !capabilities.data) throw new Error(capabilities.message || '读取 AI 配置失败')
    available.value = capabilities.data.enabled && (!props.picture.spaceId || capabilities.data.privateEnabled)
    if (props.picture.id) {
      const response = await latestAiTask(props.picture.id)
      if (response.code !== 0) throw new Error(response.message || '恢复任务失败')
      await accept(response.data, ticket)
    }
  } catch (e) { if (ticket === generation) error.value = e instanceof Error ? e.message : '读取失败' }
  finally { if (ticket === generation) loading.value = false }
}
async function create() {
  if (!available.value || !consent.value || locked.value || !props.picture.id) return
  if (!xScale.value || !yScale.value || (xScale.value === 1 && yScale.value === 1)) { error.value = '至少一个扩图倍数应大于 1'; return }
  const ticket = generation; creating.value = true; emit('busy', true); error.value = ''
  requestId ||= crypto.randomUUID()
  try {
    const response = await createAiTask({ pictureId: props.picture.id, requestId, xScale: xScale.value, yScale: yScale.value, externalProcessingConsent: true })
    if (response.code !== 0 || !response.data) throw new Error(response.message || '创建失败')
    requestId = ''; deadline = Date.now() + 10 * 60 * 1000
    if (preview.value) URL.revokeObjectURL(preview.value); preview.value = ''; previewTask = ''
    await accept(response.data, ticket)
  } catch (e) { if (ticket === generation) error.value = `${e instanceof Error ? e.message : '提交异常'}；先刷新任务，网络重试会沿用原请求标识。` }
  finally { creating.value = false; emit('busy', false) }
}
async function refresh() {
  if (checking.value || busy.value || !open.value) return
  clearTimeout(timer); const ticket = generation; checking.value = true
  try {
    const response = task.value ? await getAiTask(task.value.id) : await latestAiTask(props.picture.id!)
    if (response.code !== 0) throw new Error(response.message || '查询失败')
    error.value = ''; await accept(response.data, ticket)
  } catch (e) { if (ticket === generation) error.value = `${e instanceof Error ? e.message : '查询失败'}；可手动刷新，不会重新生成。` }
  finally { checking.value = false }
}
async function apply() {
  if (!task.value || task.value.status !== 'SUCCEEDED' || busy.value) return
  applying.value = true; emit('busy', true)
  try {
    const response = await applyAiTask(task.value.id)
    if (response.code !== 0 || !response.data) throw new Error(response.message || '保存未确认')
    await props.onSuccess?.(response.data); task.value.status = 'APPLIED'; message.success('扩图结果已应用')
  } catch (e) { error.value = `${e instanceof Error ? e.message : '保存未确认'}；先刷新任务状态和图片，不要重新生成。` }
  finally { applying.value = false; emit('busy', false); void refresh() }
}
onBeforeUnmount(cleanup)
defineExpose({ openModal })
</script>
<style scoped>
.comparison { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin: 16px 0; }
.comparison img { max-width: 100%; max-height: 280px; object-fit: contain; }
@media (max-width: 600px) { .comparison { grid-template-columns: 1fr; } }
</style>
