<template>
  <div class="picture-upload" :class="{ 'is-busy': busy }" :aria-busy="busy">
    <!-- 编辑已完成的图片时只展示当前图片，需要替换时再展开上传区 -->
    <div v-if="collapsed" class="file-preview-card">
      <img class="file-preview" :src="uploadedPreviewUrl" alt="当前图片" />
      <div class="preview-caption">
        <span><PictureOutlined /> 当前图片</span>
        <div class="preview-actions">
          <a-button size="small" :disabled="disabled" @click="handlePreview(fileList[0]!)"
            >查看大图</a-button
          >
          <a-button size="small" :disabled="disabled" @click="replacing = true">替换图片</a-button>
        </div>
      </div>
    </div>
    <template v-else>
      <p v-if="picture?.uploadStatus === 0" class="replacement-note">
        想换一张？重新选择文件即可替换这份草稿。
      </p>
      <p v-else-if="picture" class="replacement-note">
        上传新文件会替换当前图片，名称、标签等信息保持不变。
        <a-button type="link" size="small" :disabled="busy" @click="cancelReplace"
          >取消替换</a-button
        >
      </p>
      <a-tabs v-model:activeKey="uploadMode">
        <a-tab-pane key="file" tab="文件上传" :disabled="urlUploading || disabled">
          <fieldset class="upload-picker" :disabled="busy || disabled" aria-label="选择图片文件" @drop.capture="handleFileDrop">
            <a-upload-dragger
              v-model:file-list="fileList"
              class="upload-dropzone"
              accept=".jpg,.jpeg,.png,.gif,.webp"
              :show-upload-list="false"
              :multiple="false"
              :max-count="1"
              :before-upload="handleBeforeUpload"
              :custom-request="handleUpload"
              :disabled="busy || disabled"
              :aria-disabled="busy || disabled"
            >
              <div class="upload-content">
                <div class="upload-art" aria-hidden="true">
                  <span class="folder-shadow" />
                  <span class="folder-back" />
                  <span class="upload-paper paper-back"><span class="paper-landscape" /></span>
                  <span class="upload-paper paper-front"><span class="paper-landscape" /></span>
                  <span class="folder-front"><CloudUploadOutlined /></span>
                  <span class="art-spark spark-one">+</span
                  ><span class="art-spark spark-two">+</span>
                </div>
                <h3>
                  {{
                    loading
                      ? '正在上传你的图片'
                      : uploadedPreviewUrl
                        ? '拖入新图片，替换当前作品'
                        : '把这一帧，收进檐下'
                  }}
                </h3>
                <p>
                  {{
                    loading ? '请稍候，完成后即可继续完善信息' : '拖拽图片到这里，或点击选择文件'
                  }}
                </p>
                <span class="upload-choose"
                  ><LoadingOutlined v-if="loading" /><PlusOutlined v-else />{{
                    loading
                      ? processing
                        ? '处理中…'
                        : `上传中 ${progress}%`
                      : uploadedPreviewUrl
                        ? '选择替换图片'
                        : '选择图片'
                  }}</span
                >
                <span class="upload-formats"
                  >JPG · PNG · GIF · WebP<span>单张不超过 2MB</span></span
                >
              </div>
            </a-upload-dragger>
          </fieldset>
          <div v-if="loading" class="upload-status" role="status">
            <p>
              <LoadingOutlined />
              {{
                processing
                  ? '文件已送达，正在压缩并生成缩略图，请不要关闭页面。'
                  : `正在上传 ${progress}%，请不要关闭页面。`
              }}
            </p>
            <span class="upload-bar" :class="{ processing }" aria-hidden="true"
              ><span :style="{ width: `${progress}%` }"
            /></span>
          </div>
          <div v-if="uploadedPreviewUrl" class="file-preview-card">
            <img class="file-preview" :src="uploadedPreviewUrl" alt="已上传的图片预览" />
            <div class="preview-caption">
              <span><CheckCircleOutlined /> 已就绪，继续完善图片信息</span>
              <div class="preview-actions">
                <a-button
                  size="small"
                  :disabled="busy || disabled"
                  @click="handlePreview(fileList[0]!)"
                  >查看大图</a-button
                >
                <a-button size="small" :disabled="busy || disabled" @click="clearPreview"
                  >移除预览</a-button
                >
              </div>
            </div>
          </div>
        </a-tab-pane>

        <a-tab-pane key="url" tab="URL 上传" :disabled="loading || disabled">
          <div class="url-upload-panel">
            <div class="url-intro">
              <LinkOutlined />
              <div>
                <h3>从一个链接开始</h3>
                <p>粘贴图片的直链地址，导入到当前图库。</p>
              </div>
            </div>
            <a-input-search
              v-model:value="pictureUrl"
              class="url-upload"
              placeholder="请输入图片 URL"
              enter-button="提交"
              allow-clear
              :loading="urlUploading"
              :disabled="loading || urlUploading || disabled"
              @search="handleUrlUpload"
            />
            <img
              v-if="uploadedPreviewUrl"
              class="uploaded-preview"
              :src="uploadedPreviewUrl"
              alt="图片预览"
            />
          </div>
        </a-tab-pane>
      </a-tabs>
    </template>

    <!-- 图片预览弹窗 -->
    <a-modal :open="previewVisible" :footer="null" @cancel="previewVisible = false">
      <img :src="previewImage" alt="图片大图预览" style="width: 100%" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  PlusOutlined,
  LoadingOutlined,
  CloudUploadOutlined,
  LinkOutlined,
  CheckCircleOutlined,
  PictureOutlined,
} from '@ant-design/icons-vue'
import { message, Upload } from 'ant-design-vue'
import type { UploadFile } from 'ant-design-vue'
import type { AxiosProgressEvent } from 'axios'
import {
  uploadPictureByUrlUsingPost,
  uploadPictureUsingPost,
} from '@/service/api/pictureController'
import { PICTURE_UPLOAD_TIMEOUT_MS, validatePictureFile } from '@/utils/pictureUpload'

// 接收父组件传递的 props
const props = defineProps<{
  picture?: API.PictureVO
  spaceId?: string
  disabled?: boolean
  onSuccess?: (picture: API.PictureVO) => void | Promise<void>
}>()
const emit = defineEmits<{ busy: [value: boolean] }>()

const fileList = ref<UploadFile[]>([])
const previewVisible = ref(false)
const previewImage = ref('')
const loading = ref(false)
const urlUploading = ref(false)
const pictureUrl = ref('')
const uploadMode = ref<'file' | 'url'>('file')
const busy = computed(() => loading.value || urlUploading.value)
const uploadedPreviewUrl = computed(() => fileList.value[0]?.url)
// 上传进度：文件传完后服务器还要压缩和生成缩略图，此时显示“处理中”
const progress = ref(0)
const processing = computed(() => loading.value && progress.value >= 100)
const replacing = ref(false)
const collapsed = computed(
  () =>
    !!props.picture &&
    props.picture.uploadStatus !== 0 &&
    !replacing.value &&
    !busy.value &&
    !!uploadedPreviewUrl.value,
)
watch(
  () => loading.value || urlUploading.value,
  (value) => emit('busy', value),
)

function restorePreview() {
  fileList.value = props.picture?.url
    ? [{ uid: '-1', name: 'picture', status: 'done', url: props.picture.url }]
    : []
}
function clearPreview() {
  if (busy.value || props.disabled) return
  fileList.value = []
}

function cancelReplace() {
  replacing.value = false
  restorePreview()
}

// 监听父组件传入的 picture，初始化显示
watch(() => props.picture, restorePreview, { immediate: true })
watch(
  () => props.picture?.id,
  () => (replacing.value = false),
)

// 在上传组件截取第一张前检查完整拖入列表，避免其余文件被静默丢弃。
const handleFileDrop = (event: DragEvent) => {
  const count = event.dataTransfer?.files.length ?? 0
  if (count <= 1) return
  event.preventDefault()
  event.stopPropagation()
  if (!busy.value && !props.disabled) {
    message.warning(`一次只能上传一张，本次拖入了 ${count} 个文件，均未上传，请重新选择`)
  }
}

const handleBeforeUpload = (file: File, files: File[] = [file]) => {
  if (loading.value || urlUploading.value || props.disabled) return Upload.LIST_IGNORE
  if (files.length > 1) {
    if (file === files[0]) {
      message.warning(`一次只能上传一张，本次选择了 ${files.length} 个文件，均未上传，请重新选择`)
    }
    return Upload.LIST_IGNORE
  }
  const validationMessage = validatePictureFile(file)
  if (validationMessage) {
    message.error(validationMessage)
    return Upload.LIST_IGNORE
  }
  return true
}

// 自定义上传逻辑
const handleUpload = async ({ file }: { file: File }) => {
  if (loading.value || urlUploading.value || props.disabled) return
  loading.value = true
  progress.value = 0
  try {
    // 构造参数，如果有 picture.id 则传入（用于编辑）
    const params = { id: props.picture?.id, spaceId: props.spaceId }
    const res = await uploadPictureUsingPost(params, {}, file, {
      timeout: PICTURE_UPLOAD_TIMEOUT_MS,
      onUploadProgress: (event: AxiosProgressEvent) => {
        if (event.total)
          progress.value = Math.min(100, Math.round((event.loaded / event.total) * 100))
      },
    })

    if (res.code === 0 && res.data) {
      replacing.value = false
      message.success(props.picture ? '图片已替换' : '图片已上传为草稿，请完善信息后提交')
      // 更新文件状态为成功，并设置 URL
      fileList.value = [
        {
          uid: '-1',
          name: file.name,
          status: 'done',
          url: res.data.url,
        },
      ]
      // 将上传成功的图片信息传递给父组件
      await props.onSuccess?.(res.data)
    } else {
      message.error('图片上传失败，' + res.message)
      // 上传失败，清空文件列表
      restorePreview()
    }
  } catch (error) {
    message.error('图片上传失败，' + (error as Error).message)
    // 上传失败，清空文件列表
    restorePreview()
  } finally {
    loading.value = false
  }
}

const handleUrlUpload = async (value: string) => {
  if (urlUploading.value || loading.value || props.disabled) return
  const fileUrl = value.trim()
  if (!fileUrl) {
    message.warning('请输入图片 URL')
    return
  }

  urlUploading.value = true
  try {
    const res = await uploadPictureByUrlUsingPost(
      {
        fileUrl,
        id: props.picture?.id,
        spaceId: props.spaceId,
      },
      { timeout: PICTURE_UPLOAD_TIMEOUT_MS },
    )

    if (res.code === 0 && res.data) {
      message.success(props.picture ? '图片已替换' : '图片已导入为草稿，请完善信息后提交')
      fileList.value = [
        {
          uid: '-1',
          name: res.data.name || 'url-picture',
          status: 'done',
          url: res.data.url,
        },
      ]
      pictureUrl.value = ''
      replacing.value = false
      await props.onSuccess?.(res.data)
    } else {
      message.error('图片导入失败，' + res.message)
    }
  } catch (error) {
    message.error('图片导入失败，' + (error as Error).message)
  } finally {
    urlUploading.value = false
  }
}

// 预览图片
const handlePreview = async (file: UploadFile) => {
  if (!file.url && !file.preview) {
    file.preview = await getBase64(file.originFileObj as File)
  }
  previewImage.value = (file.url || file.preview) as string
  previewVisible.value = true
}

// 文件转 Base64
const getBase64 = (file: File): Promise<string> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.readAsDataURL(file)
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = (error) => reject(error)
  })
}
</script>

<style scoped>
.picture-upload {
  padding: 8px 0 0;
}
.replacement-note {
  margin: 0 0 16px;
  font-size: 12px;
  color: var(--muted);
}
.replacement-note :deep(.ant-btn-link) {
  height: auto;
  padding: 0 4px;
  font-size: 12px;
}
.picture-upload :deep(.ant-tabs-nav) {
  margin-bottom: 24px;
}
.upload-picker {
  min-width: 0;
  margin: 0;
  padding: 0;
  border: 0;
}
.picture-upload :deep(.ant-upload.ant-upload-drag) {
  /* Ant's default 100% height overflows a tab that also contains the preview. */
  height: auto;
  background: #fafafb;
  border: 1px dashed #d4d4db;
  border-radius: 20px;
  overflow: hidden;
  transition:
    transform 320ms var(--motion-ease),
    box-shadow 320ms var(--motion-ease),
    border-color 200ms,
    background-color 200ms;
}
.picture-upload :deep(.ant-upload.ant-upload-drag .ant-upload-btn) {
  padding: 0;
}
.upload-content {
  padding: 32px 20px 28px;
}
.upload-content h3 {
  margin: 12px 0 9px;
  color: var(--ink);
  font-size: 22px;
  font-weight: 600;
}
.upload-content p {
  margin: 0 0 22px;
  color: var(--muted);
  font-size: 13px;
}
.upload-choose {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 22px;
  border-radius: 100px;
  background: var(--ink);
  color: white;
  font-size: 13px;
  transition:
    transform 280ms var(--motion-ease),
    box-shadow 280ms var(--motion-ease);
}
.upload-formats {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px 18px;
  margin-top: 18px;
  color: var(--muted);
  font-size: 11px;
}
.upload-art {
  position: relative;
  width: 160px;
  height: 128px;
  margin: 0 auto;
  perspective: 600px;
}
.folder-shadow {
  position: absolute;
  width: 104px;
  height: 9px;
  left: 28px;
  bottom: 0;
  background: #1d1d1f12;
  border-radius: 50%;
  filter: blur(4px);
  transition:
    transform 360ms var(--motion-ease),
    opacity 360ms;
}
.folder-back {
  position: absolute;
  left: 22px;
  top: 37px;
  width: 116px;
  height: 78px;
  background: #4e5056;
  border-radius: 8px 12px 12px 12px;
}
.folder-back::before {
  content: '';
  position: absolute;
  left: 0;
  top: -10px;
  width: 48px;
  height: 19px;
  background: inherit;
  border-radius: 8px 13px 0 0;
}
.upload-paper {
  position: absolute;
  left: 39px;
  top: 24px;
  display: block;
  width: 82px;
  height: 79px;
  padding: 6px 6px 14px;
  background: white;
  border: 1px solid #e8e8ed;
  border-radius: 7px;
  box-shadow: 0 2px 5px #00000008;
  transition: transform 450ms var(--motion-ease);
}
.paper-back {
  transform: rotate(-7deg);
}
.paper-front {
  transform: translate(5px, 6px) rotate(6deg);
}
.paper-landscape {
  position: relative;
  display: block;
  height: 100%;
  overflow: hidden;
  border-radius: 3px;
  background: linear-gradient(145deg, #dce9fc, #c7d7ef);
}
.paper-landscape::before {
  content: '';
  position: absolute;
  width: 15px;
  height: 15px;
  right: 9px;
  top: 9px;
  border-radius: 50%;
  background: #ffffffdf;
}
.paper-landscape::after {
  content: '';
  position: absolute;
  inset: 19px -5px -5px;
  background: #7599bd;
  clip-path: polygon(0 100%, 38% 15%, 68% 68%, 82% 44%, 100% 100%);
}
.paper-front .paper-landscape {
  background: linear-gradient(145deg, #e6f3ec, #bedbca);
}
.paper-front .paper-landscape::after {
  background: #669e82;
}
.folder-front {
  position: absolute;
  left: 22px;
  top: 51px;
  width: 116px;
  height: 66px;
  border-radius: 10px 10px 12px 12px;
  display: grid;
  place-items: center;
  color: #ffffffb8;
  background: linear-gradient(140deg, #42454b, #222327);
  transform-origin: center bottom;
  transition:
    transform 450ms var(--motion-ease),
    box-shadow 450ms;
  box-shadow: 0 4px 8px #0000000b;
  font-size: 25px;
}
.art-spark {
  position: absolute;
  font-size: 17px;
  color: #929ba9;
  opacity: 0;
  transition:
    transform 400ms var(--motion-ease),
    opacity 240ms;
}
.spark-one {
  left: 5px;
  top: 23px;
  transform: translate(5px, 8px) rotate(-15deg);
}
.spark-two {
  right: 3px;
  top: 52px;
  font-size: 12px;
  transform: translate(-5px, 8px) rotate(15deg);
}
.picture-upload :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):focus-within),
.picture-upload :deep(.ant-upload.ant-upload-drag.ant-upload-drag-hover) {
  border-color: #858b96;
  background: #f5f6f9;
  box-shadow: 0 0 0 3px #1d1d1f06;
}
.picture-upload :deep(.ant-upload-disabled) {
  cursor: not-allowed;
}
.picture-upload :deep(.ant-upload-disabled .upload-choose) {
  background: #dedee3;
  color: var(--muted);
}
.upload-status {
  margin: 16px auto 0;
  max-width: 360px;
  color: var(--muted);
  font-size: 12px;
  text-align: center;
}
.upload-status p {
  margin: 0 0 10px;
}
.upload-bar {
  display: block;
  height: 3px;
  overflow: hidden;
  border-radius: 3px;
  background: var(--line);
}
.upload-bar > span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--ink);
  transition: width 200ms linear;
}
.upload-bar.processing > span {
  animation: upload-processing 1.4s ease-in-out infinite;
}
@keyframes upload-processing {
  50% {
    opacity: 0.35;
  }
}
@media (prefers-reduced-motion: reduce) {
  .upload-bar > span {
    transition: none;
  }
  .upload-bar.processing > span {
    animation: none;
  }
}
.file-preview-card {
  margin-top: 24px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 16px;
  background: var(--surface-soft);
}
.file-preview {
  display: block;
  width: 100%;
  max-height: 400px;
  padding: 16px;
  object-fit: contain;
}
.preview-caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  padding: 14px 16px;
  background: white;
  color: var(--muted);
  font-size: 11px;
}
.preview-caption > span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}
.preview-actions {
  display: flex;
  gap: 8px;
}
.url-upload-panel {
  width: 100%;
  padding: 28px;
  background: #fafafb;
  border: 1px solid var(--line);
  border-radius: 20px;
}
.url-intro {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}
.url-intro > .anticon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: 14px;
  background: white;
  border: 1px solid var(--line);
  font-size: 21px;
}
.url-intro h3 {
  margin: 0 0 4px;
  font-size: 17px;
  font-weight: 600;
}
.url-intro p {
  margin: 0;
  color: var(--muted);
  font-size: 12px;
}
.url-upload {
  width: 100%;
}
.uploaded-preview {
  display: block;
  width: 100%;
  max-height: 480px;
  margin-top: 24px;
  border-radius: 12px;
  object-fit: contain;
}
@media (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .picture-upload :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):hover) {
    transform: translateY(-3px);
    border-color: #9498a1;
    background: #f7f8fa;
    box-shadow: 0 12px 30px #1d1d1f0a;
  }
  .picture-upload :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):hover .paper-back) {
    transform: translate(-9px, -15px) rotate(-15deg);
  }
  .picture-upload :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):hover .paper-front) {
    transform: translate(13px, -23px) rotate(11deg);
  }
  .picture-upload :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):hover .folder-front) {
    transform: rotateX(-26deg) skewX(-3deg);
    box-shadow: 0 8px 14px #00000010;
  }
  .picture-upload
    :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):hover .folder-shadow) {
    transform: scaleX(1.12);
    opacity: 0.7;
  }
  .picture-upload :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):hover .art-spark) {
    opacity: 0.75;
    transform: translateY(-4px) rotate(0deg);
  }
  .picture-upload
    :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):hover .upload-choose) {
    transform: translateY(-1px);
    box-shadow: 0 5px 12px #1d1d1f16;
  }
  .picture-upload :deep(.ant-upload.ant-upload-drag:not(.ant-upload-disabled):active) {
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: no-preference) {
  .picture-upload
    :deep(.ant-upload.ant-upload-drag.ant-upload-drag-hover:not(.ant-upload-disabled)) {
    transform: translateY(-3px);
  }
  .picture-upload :deep(.ant-upload-drag-hover:not(.ant-upload-disabled) .paper-back) {
    transform: translate(-9px, -15px) rotate(-15deg);
  }
  .picture-upload :deep(.ant-upload-drag-hover:not(.ant-upload-disabled) .paper-front) {
    transform: translate(13px, -23px) rotate(11deg);
  }
  .picture-upload :deep(.ant-upload-drag-hover:not(.ant-upload-disabled) .folder-front) {
    transform: rotateX(-26deg) skewX(-3deg);
  }
}
@media (max-width: 640px) {
  .upload-content {
    padding: 28px 12px 24px;
  }
  .upload-content h3 {
    font-size: 20px;
  }
  .upload-content p {
    font-size: 12px;
  }
  .url-upload-panel {
    padding: 20px 16px;
  }
  .url-intro {
    gap: 12px;
  }
  .url-intro h3 {
    font-size: 16px;
  }
}
</style>
