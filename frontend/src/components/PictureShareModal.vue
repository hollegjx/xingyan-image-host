<template>
  <a-modal :open="open" title="分享图片" :footer="null" @cancel="$emit('update:open', false)">
    <a-alert
      v-if="picture?.spaceId != null"
      type="warning"
      show-icon
      message="空间里的图片没有公开直链"
      description="本站详情页链接需要登录并具有空间权限；主人或团队管理员可另行生成限时的展示图直链。"
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
      <section
        v-if="canGeneratePrivateLink && picture?.spaceId != null && picture?.uploadStatus === 1"
        class="embed"
        aria-label="私有图片临时直链"
      >
        <a-alert
          type="warning"
          show-icon
          message="拿到链接的人在有效期内都能看到这张图"
          description="只分享独立展示图，不提供原图。链接到期前无法通过撤销团队成员收回，请谨慎转发。"
        />
        <label for="private-link-hours">有效期</label>
        <select id="private-link-hours" v-model.number="validHours" @change="clearTemporaryLink">
          <option :value="1">1 小时</option>
          <option :value="24">24 小时</option>
          <option :value="168">7 天</option>
        </select>
        <a-button :loading="generating" @click="generateTemporaryLink">生成临时直链</a-button>
        <template v-if="temporaryUrl">
          <a-input
            id="private-temporary-link"
            :value="temporaryUrl"
            readonly
            aria-label="临时直链"
            @focus="selectLink"
          />
          <p>到期时间：{{ expiresAt }}</p>
          <a-button @click="copyTemporaryLink">复制临时直链</a-button>
        </template>
        <p role="status">{{ temporaryStatus }}</p>
      </section>
      <!-- 永久直链只针对审核通过的公共图片。 -->
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
import { generatePrivatePictureLink } from '@/service/api/picturePublicationController'
const props = defineProps<{
  open: boolean
  picture?: API.PictureVO
  canGeneratePrivateLink?: boolean
}>()
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
const validHours = ref(1)
const temporaryUrl = ref('')
const expiresAt = ref('')
const generating = ref(false)
const temporaryStatus = ref('')
let temporarySequence = 0
function clearTemporaryLink() {
  temporarySequence++
  temporaryUrl.value = ''
  expiresAt.value = ''
  temporaryStatus.value = ''
  generating.value = false
}
watch(() => [props.open, props.picture?.id, props.canGeneratePrivateLink], clearTemporaryLink)
async function generateTemporaryLink() {
  if (!props.open || !props.canGeneratePrivateLink || !props.picture?.id || generating.value) return
  clearTemporaryLink()
  const ticket = temporarySequence
  generating.value = true
  try {
    const result = await generatePrivatePictureLink(props.picture.id, validHours.value)
    if (ticket !== temporarySequence) return
    if (result.code !== 0 || !result.data?.url) {
      temporaryStatus.value = result.message || '生成失败，请重试'
      return
    }
    temporaryUrl.value = result.data.url
    expiresAt.value = new Date(result.data.expiresAt).toLocaleString()
  } catch {
    if (ticket === temporarySequence) temporaryStatus.value = '生成失败，请重试'
  } finally {
    if (ticket === temporarySequence) generating.value = false
  }
}
async function copyTemporaryLink() {
  const value = temporaryUrl.value
  const ticket = temporarySequence
  if (!value) return
  try {
    await navigator.clipboard.writeText(value)
    if (ticket === temporarySequence) temporaryStatus.value = '临时直链已复制'
  } catch {
    if (ticket === temporarySequence) temporaryStatus.value = '自动复制失败，请手动复制'
  }
}
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
