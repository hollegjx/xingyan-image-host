<template>
  <a-modal :open="open" title="编辑图片" :footer="null" :width="720" :mask-closable="!saving" :closable="!saving" @cancel="close">
    <a-alert v-if="error" type="error" :message="error" show-icon />
    <a-spin v-if="loading" />
    <template v-if="imageUrl">
      <a-radio-group v-model:value="ratio" style="margin: 16px 0">
        <a-radio-button v-for="r in ['自由', '1:1', '4:3', '16:9', '3:4', '9:16']" :key="r" :value="r">{{ r }}</a-radio-button>
      </a-radio-group>
      <div class="crop-area">
        <VueCropper ref="cropper" :img="imageUrl" :auto-crop="true" :fixed="ratio !== '自由'" :fixed-number="fixedNumber"
          :center-box="true" :can-move-box="true" :high="false" :full="true" :max-img-size="4096" output-type="png" @img-load="onImageLoad" />
      </div>
      <a-space wrap style="margin-top: 16px">
        <a-button :disabled="saving || !ready" @click="cropper?.rotateLeft()">向左旋转</a-button>
        <a-button :disabled="saving || !ready" @click="cropper?.rotateRight()">向右旋转</a-button>
        <a-button :disabled="saving || !ready" @click="cropper?.changeScale(1)">放大</a-button>
        <a-button :disabled="saving || !ready" @click="cropper?.changeScale(-1)">缩小</a-button>
        <a-button type="primary" :loading="saving" :disabled="!ready || !!error" @click="save">保存并替换</a-button>
      </a-space>
      <p>仅在确认后替换原图；公共图片按原有规则重新审核。PNG 导出不能超过 2 MB。</p>
    </template>
  </a-modal>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
// 1.1.4's published declarations import an untyped internal SFC; use its public built module.
import { VueCropper } from 'vue-cropper/dist/vue-cropper.es.js'
import 'vue-cropper/dist/index.css'
import { message } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/service/api/pictureController'
import { editingBlob, editingSource } from '@/service/aiEditing'
import { validatePictureFile, PICTURE_UPLOAD_TIMEOUT_MS } from '@/utils/pictureUpload'

const props = defineProps<{ picture: API.PictureVO; onSuccess?: (p: API.PictureVO) => void | Promise<void> }>()
const emit = defineEmits<{ busy: [value: boolean] }>()
interface Cropper { rotateLeft(): void; rotateRight(): void; changeScale(n: number): void; getCropBlob(callback: (blob: Blob) => void): void }
const cropper = ref<Cropper>()
const open = ref(false), loading = ref(false), saving = ref(false), ready = ref(false)
const imageUrl = ref(''), error = ref(''), ratio = ref('自由')
const fixedNumber = computed(() => ratio.value === '自由' ? [1, 1] : ratio.value.split(':').map(Number))
let version = 0, abort: AbortController | undefined
const release = () => { abort?.abort(); if (imageUrl.value) URL.revokeObjectURL(imageUrl.value); imageUrl.value = ''; version++ }
const close = () => { if (saving.value) return; open.value = false; release() }
async function openModal() {
  release(); const ticket = version
  open.value = true; loading.value = true; error.value = ''; ready.value = false; ratio.value = '自由'
  abort = new AbortController()
  try {
    if (!props.picture.id) throw new Error('请先上传图片')
    const blob = await editingBlob(editingSource(props.picture.id), abort.signal)
    if (ticket === version) imageUrl.value = URL.createObjectURL(blob)
  } catch (e) { if (ticket === version) error.value = e instanceof Error ? e.message : '读取失败' }
  finally { if (ticket === version) loading.value = false }
}
function onImageLoad(status: string) { ready.value = status === 'success'; if (status === 'error') error.value = '图片解码失败，请使用普通静态图片' }
async function save() {
  if (saving.value || !ready.value || !cropper.value) return
  saving.value = true; emit('busy', true)
  try {
    const blob = await new Promise<Blob>((resolve, reject) => {
      const timer = window.setTimeout(() => reject(new Error('裁剪导出超时')), 10000)
      cropper.value!.getCropBlob((data) => {
        window.clearTimeout(timer)
        if (data?.size) resolve(data)
        else reject(new Error('裁剪结果为空'))
      })
    })
    const file = new File([blob], 'edited.png', { type: 'image/png' })
    const invalid = validatePictureFile(file); if (invalid) throw new Error(invalid)
    const response = await uploadPictureUsingPost(
      { id: props.picture.id, spaceId: props.picture.spaceId },
      { name: props.picture.name, introduction: props.picture.introduction, category: props.picture.category, tags: props.picture.tags },
      file, { timeout: PICTURE_UPLOAD_TIMEOUT_MS },
    )
    if (response.code !== 0 || !response.data) throw new Error(response.message || '保存失败')
    await props.onSuccess?.(response.data)
    message.success('图片已替换'); open.value = false; release()
  } catch (e) { message.error(e instanceof Error ? e.message : '保存失败；网络中断时请刷新检查图片') }
  finally { saving.value = false; emit('busy', false) }
}
onBeforeUnmount(release)
defineExpose({ openModal })
</script>
<style scoped>
.crop-area { height: 400px; width: 100%; }
p { margin-top: 12px; color: #666; }
</style>
