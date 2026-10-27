<template>
  <main class="collaboration-page">
    <div class="heading">
      <a-button @click="router.push(`/picture/${id}`)">返回图片</a-button>
      <h2>团队图片协同编辑</h2>
      <a-tag :color="connected ? 'green' : 'default'">{{ connected ? '已连接' : '未连接' }}</a-tag>
    </div>
    <a-alert
      type="info"
      show-icon
      message="一人编辑，其他编辑成员实时查看"
      description="支持旋转和缩放。放大会裁去边缘，缩小会留下透明边缘；点击保存后才会替换图片。全部成员离开或服务重启会丢失未保存操作。"
    />
    <a-alert v-if="error" class="notice" type="error" show-icon :message="error" />
    <p aria-live="polite">{{ status }}</p>
    <a-space wrap class="toolbar">
      <a-button v-if="!connected" :loading="connecting" @click="connect">重新连接</a-button>
      <a-button
        v-if="!state?.editing"
        type="primary"
        :disabled="!ready || !!state?.editorId || pending"
        @click="send('ENTER_EDIT')"
        >取得编辑权</a-button
      >
      <a-button v-else :disabled="pending || saving" @click="send('EXIT_EDIT')"
        >释放编辑权</a-button
      >
      <a-button :disabled="!canEdit" @click="send('EDIT_ACTION', 'ROTATE_LEFT')">向左旋转</a-button>
      <a-button :disabled="!canEdit" @click="send('EDIT_ACTION', 'ROTATE_RIGHT')"
        >向右旋转</a-button
      >
      <a-button
        aria-label="放大"
        :disabled="!canEdit || state!.zoom >= 8"
        @click="send('EDIT_ACTION', 'ZOOM_IN')"
        >放大</a-button
      >
      <a-button
        aria-label="缩小"
        :disabled="!canEdit || state!.zoom <= -8"
        @click="send('EDIT_ACTION', 'ZOOM_OUT')"
        >缩小</a-button
      >
      <a-button type="primary" :disabled="!canEdit" :loading="saving" @click="save"
        >保存图片</a-button
      >
    </a-space>
    <div class="preview" :aria-busy="loadingImage">
      <a-spin v-if="loadingImage" />
      <canvas v-show="ready" ref="canvas" aria-label="协同编辑预览" />
      <p v-if="!ready && !loadingImage">连接并加载图片后显示预览</p>
    </div>
    <p v-if="state" class="version">
      当前版本 {{ state.revision }} · 旋转 {{ state.rotation * 90 }}° · 缩放
      {{ Math.round(1.1 ** state.zoom * 100) }}%
    </p>
  </main>
</template>

<script setup lang="ts">
import { ref, computed, onBeforeUnmount, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { getPictureVoByIdUsingGet, uploadPictureUsingPost } from '@/service/api/pictureController'
import { editingBlob, editingSource } from '@/service/aiEditing'
import { collaborationUrl, readEditState, drawEdit, type EditState } from '@/utils/collaboration'

const props = defineProps<{ id: string }>()
const router = useRouter()
const canvas = ref<HTMLCanvasElement>()
const state = ref<EditState>()
const connected = ref(false),
  connecting = ref(false),
  loadingImage = ref(false),
  loaded = ref(false)
const pending = ref(false),
  saving = ref(false),
  error = ref('')
const ready = computed(
  () => connected.value && loaded.value && !!state.value && !loadingImage.value,
)
const canEdit = computed(
  () =>
    ready.value &&
    !!state.value?.editing &&
    !state.value?.saving &&
    !pending.value &&
    !saving.value,
)
const status = computed(() =>
  !connected.value
    ? '连接断开时不能操作，重新连接不会自动重试保存。'
    : state.value?.saving
      ? '图片正在保存…'
      : state.value?.editing
        ? '你持有编辑权'
        : state.value?.editorId
          ? `${state.value.editorName || '另一位成员'}正在编辑`
          : '当前无人编辑',
)
let socket: WebSocket | undefined
let heartbeat: ReturnType<typeof setInterval> | undefined
let ackTimer: ReturnType<typeof setTimeout> | undefined
let abort: AbortController | undefined
let image: HTMLImageElement | undefined
let objectUrl: string | undefined
let loadedSource = '',
  epoch = 0

function clearImage() {
  abort?.abort()
  image = undefined
  loaded.value = false
  loadedSource = ''
  loadingImage.value = false
  if (objectUrl) URL.revokeObjectURL(objectUrl)
  objectUrl = undefined
  if (canvas.value) {
    canvas.value.width = 1
    canvas.value.height = 1
  }
}
function close() {
  epoch++
  clearInterval(heartbeat)
  clearTimeout(ackTimer)
  if (socket) {
    socket.onclose = null
    socket.onmessage = null
    socket.close()
    socket = undefined
  }
  connected.value = false
  connecting.value = false
  pending.value = false
  state.value = undefined
  clearImage()
}
async function loadSource(source: string, ticket: number) {
  clearImage()
  loadingImage.value = true
  loadedSource = source
  const controller = new AbortController()
  abort = controller
  try {
    const blob = await editingBlob(editingSource(props.id), controller.signal)
    if (ticket !== epoch || controller.signal.aborted) return
    const url = URL.createObjectURL(blob)
    objectUrl = url
    const next = new Image()
    next.src = url
    await next.decode()
    if (ticket !== epoch || controller.signal.aborted) return
    image = next
    await nextTick()
    render()
    loaded.value = true
  } catch (e) {
    if (ticket === epoch && !controller.signal.aborted) {
      error.value = e instanceof Error ? e.message : '图片加载失败'
      loadedSource = ''
    }
  } finally {
    if (ticket === epoch && !controller.signal.aborted) loadingImage.value = false
  }
}
function render() {
  if (canvas.value && image && state.value) drawEdit(canvas.value, image, state.value)
}
function connect() {
  close()
  error.value = ''
  connecting.value = true
  const ticket = epoch
  try {
    const ws = new WebSocket(collaborationUrl(props.id))
    socket = ws
    ws.onopen = () => {
      if (ticket !== epoch) return
      connected.value = true
      connecting.value = false
      heartbeat = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify({ type: 'HEARTBEAT' }))
      }, 15000)
    }
    ws.onmessage = (event) => {
      if (ticket !== epoch) return
      try {
        const data = JSON.parse(event.data)
        const next = readEditState(data.state)
        if (data.type === 'ERROR' || next.revision !== state.value?.revision) {
          pending.value = false
          clearTimeout(ackTimer)
        }
        state.value = next
        if (data.type === 'ERROR')
          error.value = typeof data.message === 'string' ? data.message : '操作未完成'
        if (next.sourceVersion !== loadedSource) void loadSource(next.sourceVersion, ticket)
        else render()
      } catch {
        error.value = '协同状态异常，请重新连接'
        close()
      }
    }
    ws.onclose = () => {
      if (ticket !== epoch) return
      close()
      error.value = '连接已断开：登录、编辑权限或网络可能已变化，请重新连接。'
    }
    ws.onerror = () => {
      if (ticket === epoch) error.value = '连接失败，仅团队空间内有编辑权限的成员可加入。'
    }
  } catch (e) {
    close()
    error.value = e instanceof Error ? e.message : '连接失败'
  }
}
function send(type: string, action?: string) {
  if (!socket || socket.readyState !== WebSocket.OPEN || pending.value || !state.value) return
  error.value = ''
  pending.value = true
  socket.send(JSON.stringify({ type, action, revision: state.value.revision }))
  ackTimer = setTimeout(() => {
    error.value = '操作确认超时，请重新连接后核对状态'
    close()
  }, 10000)
}
async function save() {
  if (!canEdit.value || !canvas.value || !state.value) return
  saving.value = true
  error.value = ''
  const ticket = epoch,
    snapshot = state.value,
    pictureId = props.id
  try {
    const blob = await new Promise<Blob>((resolve, reject) =>
      canvas.value!.toBlob(
        (b) => (b ? resolve(b) : reject(new Error('图片导出失败'))),
        'image/png',
      ),
    )
    if (blob.size > 2 * 1024 * 1024) throw new Error('编辑结果超过 2MB，请调整图片后重试')
    const detail = await getPictureVoByIdUsingGet({ id: pictureId })
    if (detail.code !== 0 || !detail.data) throw new Error(detail.message || '图片信息读取失败')
    if (ticket !== epoch || !connected.value) return
    const picture = detail.data
    const result = await uploadPictureUsingPost(
      { id: pictureId },
      {
        name: picture.name,
        introduction: picture.introduction,
        category: picture.category,
        tags: picture.tags,
        collaborationToken: snapshot.leaseToken,
        collaborationRevision: snapshot.revision,
      },
      new File([blob], 'collaborative-edit.png', { type: 'image/png' }),
      { timeout: 120000 },
    )
    if (ticket !== epoch) return
    if (result.code !== 0) throw new Error(result.message || '保存失败')
    message.success('协同编辑结果已保存')
  } catch (e) {
    if (ticket === epoch)
      error.value = `${e instanceof Error ? e.message : '保存失败'}。若请求超时，请先核对图片结果，不要直接重复提交。`
  } finally {
    saving.value = false
  }
}
watch(() => props.id, connect, { immediate: true })
onBeforeUnmount(close)
</script>

<style scoped>
.collaboration-page {
  max-width: 1100px;
  margin: auto;
  padding: 20px;
}
h2 {
  margin: 0;
  font-size: 22px;
}
.notice,
.toolbar {
  margin-top: 16px;
}
.heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}
.preview {
  margin-top: 20px;
  min-height: 260px;
  padding: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #f1f3f5;
  border: 1px solid #ddd;
  border-radius: 8px;
}
canvas {
  display: block;
  max-width: 100%;
  max-height: 65vh;
  object-fit: contain;
  background: repeating-conic-gradient(#eee 0 25%, #fff 0 50%) 0 / 20px 20px;
}
.version {
  color: #666;
}
@media (max-width: 600px) {
  .collaboration-page {
    padding: 12px;
  }
  h2 {
    font-size: 18px;
  }
  .preview {
    padding: 4px;
  }
}
</style>
