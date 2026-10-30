<template>
  <a-modal :open="open" title="分享图片" :footer="null" @cancel="$emit('update:open', false)">
    <a-alert
      v-if="picture?.spaceId != null"
      type="warning"
      show-icon
      message="空间里的图片没有公开直链"
      description="下面的链接只有能进入这个空间的人登录后才能打开。需要直链或 Markdown 外链时，请把图片上传到公共图库，审核通过后就能在这里复制。"
    />
    <a-alert
      v-else-if="picture?.uploadStatus !== 1 || picture?.reviewStatus !== 1"
      type="warning"
      show-icon
      message="这张图片尚未公开，接收者仍需通过权限校验"
    />
    <template v-if="link">
      <p>分享这张图片在本站的详情页。</p>
      <label for="picture-share-link">图片链接</label>
      <a-input id="picture-share-link" :value="link" readonly @focus="selectLink" />
      <a-button class="copy-button" :loading="copying" @click="copy">复制链接</a-button>
      <p role="status">{{ copyStatus }}</p>
      <!-- 只为已公开的公共图片提供直链，私有图片的地址带访问签名，绝不展示 -->
      <section v-if="directUrl" class="embed" aria-label="图片直链与引用">
        <span class="embed-title">直链与引用</span>
        <a-segmented v-model:value="format" :options="formatOptions" block />
        <a-input
          id="picture-embed-code"
          :value="embedCode"
          readonly
          :aria-label="`${formatLabel}代码`"
          @focus="selectLink"
        />
        <a-button class="copy-button" @click="copyEmbed">复制{{ formatLabel }}</a-button>
        <p role="status">{{ embedStatus }}</p>
      </section>
      <div class="qr"><a-qrcode :value="link" :size="200" /></div>
      <p>
        手机扫码查看。{{
          localHost
            ? '当前是本机地址，其他设备通常无法访问；上线后请使用网站域名。'
            : '访问权限与打开链接相同。'
        }}
      </p>
    </template>
    <a-alert v-else type="error" message="无法生成分享链接" />
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { pictureShareLink } from '@/utils/pictureExtensions'
const props = defineProps<{ open: boolean; picture?: API.PictureVO }>()
defineEmits<{ 'update:open': [value: boolean] }>()
const copying = ref(false)
const copyStatus = ref('')
const link = computed(() => {
  try {
    return pictureShareLink(props.picture?.id, window.location.origin, import.meta.env.BASE_URL)
  } catch {
    return ''
  }
})
const localHost = computed(() =>
  ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname),
)
type EmbedFormat = 'url' | 'markdown' | 'html' | 'bbcode'
const formatOptions: { label: string; value: EmbedFormat }[] = [
  { label: '直链', value: 'url' },
  { label: 'Markdown', value: 'markdown' },
  { label: 'HTML', value: 'html' },
  { label: 'BBCode', value: 'bbcode' },
]
const format = ref<EmbedFormat>('url')
const embedStatus = ref('')
const formatLabel = computed(() => formatOptions.find((o) => o.value === format.value)!.label)
const directUrl = computed(() => {
  const picture = props.picture
  if (
    !picture ||
    picture.spaceId != null ||
    picture.uploadStatus !== 1 ||
    picture.reviewStatus !== 1
  )
    return ''
  try {
    const url = new URL(picture.url || '')
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.href : ''
  } catch {
    return ''
  }
})
const embedCode = computed(() => {
  const url = directUrl.value
  const name = props.picture?.name || '图片'
  if (format.value === 'markdown') return `![${name.replace(/[[\]]/g, '')}](${url})`
  if (format.value === 'html') {
    const alt = name.replace(
      /[&<>"]/g,
      (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' })[c]!,
    )
    return `<img src="${url}" alt="${alt}" />`
  }
  if (format.value === 'bbcode') return `[img]${url}[/img]`
  return url
})
watch(
  () => [props.open, props.picture?.id],
  () => {
    copyStatus.value = ''
    embedStatus.value = ''
  },
)
watch(format, () => {
  embedStatus.value = ''
})
async function copyEmbed() {
  const value = embedCode.value
  try {
    if (!navigator.clipboard?.writeText) throw new Error('clipboard unavailable')
    await navigator.clipboard.writeText(value)
    if (value === embedCode.value && props.open) embedStatus.value = `${formatLabel.value}已复制`
  } catch {
    if (value === embedCode.value && props.open)
      embedStatus.value = '自动复制失败，请选中上方内容手动复制'
  }
}
function selectLink(event: FocusEvent) {
  ;(event.target as HTMLInputElement)?.select()
}
async function copy() {
  if (!link.value || copying.value) return
  copying.value = true
  const value = link.value
  try {
    if (!navigator.clipboard?.writeText) throw new Error('clipboard unavailable')
    await navigator.clipboard.writeText(value)
    if (value === link.value && props.open) copyStatus.value = '链接已复制'
  } catch {
    if (value === link.value && props.open)
      copyStatus.value = '自动复制失败，请选中上方链接手动复制'
  } finally {
    copying.value = false
  }
}
</script>

<style scoped>
.copy-button {
  margin-top: 12px;
}
.embed {
  display: grid;
  gap: 10px;
  margin-top: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--line);
}
.embed-title {
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.embed .copy-button {
  justify-self: start;
  margin-top: 0;
}
.embed p {
  margin: 0;
}
.qr {
  display: flex;
  justify-content: center;
  margin: 16px 0;
}
</style>
