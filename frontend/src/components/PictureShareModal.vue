<template>
  <a-modal :open="open" title="分享图片" :footer="null" @cancel="$emit('update:open', false)">
    <a-alert
      v-if="picture?.spaceId != null"
      type="warning"
      show-icon
      message="私人图片链接不会授予访问权限"
      description="接收者仍须登录并通过空间权限校验。当前仅空间所有者可查看，复制链接不会公开图片。"
    />
    <a-alert
      v-else-if="picture?.uploadStatus !== 1 || picture?.reviewStatus !== 1"
      type="warning"
      show-icon
      message="这张图片尚未公开，接收者仍需通过权限校验"
    />
    <template v-if="link">
      <p>分享的是本站详情页，不是 COS 文件地址。</p>
      <label for="picture-share-link">图片链接</label>
      <a-input id="picture-share-link" :value="link" readonly @focus="selectLink" />
      <a-button class="copy-button" :loading="copying" @click="copy">复制链接</a-button>
      <p role="status">{{ copyStatus }}</p>
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
watch(
  () => [props.open, props.picture?.id],
  () => {
    copyStatus.value = ''
  },
)
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
.qr {
  display: flex;
  justify-content: center;
  margin: 16px 0;
}
</style>
