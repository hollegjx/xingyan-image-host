<template>
  <div
    class="picture-thumbnail"
    :class="{ 'is-loading': !!src && loadedSrc !== src }"
    :style="{ width, height: `${height}px` }"
  >
    <img
      v-if="src"
      :key="src"
      :src="src"
      :alt="picture.name || '图片'"
      :class="{ 'is-loaded': loadedSrc === src }"
      loading="lazy"
      decoding="async"
      @load="handleLoad"
      @error="handleError"
    />
    <span
      v-else
      class="image-unavailable"
      role="img"
      :aria-label="`${picture.name || '图片'}：加载失败`"
    >
      图片暂不可用
    </span>
    <button
      v-if="preview && picture.url?.trim()"
      type="button"
      class="preview-trigger"
      :aria-label="`预览 ${picture.name || '图片'}`"
      @click="previewOpen = true"
    />
    <!-- Only mount the large image when requested; the list must not preload it. -->
    <a-image
      v-if="previewOpen"
      :src="picture.url"
      :alt="picture.name || '图片'"
      :style="{ display: 'none' }"
      :preview="{ visible: previewOpen, onVisibleChange: setPreviewOpen }"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Image as AImage } from 'ant-design-vue'

const props = withDefaults(
  defineProps<{
    picture: Pick<API.PictureVO, 'id' | 'url' | 'thumbnailUrl' | 'name'>
    preview?: boolean
    width?: string
    height?: number
  }>(),
  { preview: false, width: '100%', height: 180 },
)

const failedSources = ref<string[]>([])
const loadedSrc = ref('')
const previewOpen = ref(false)
const src = computed(() =>
  [props.picture.thumbnailUrl, props.picture.url]
    .map((url) => url?.trim())
    .find((url): url is string => !!url && !failedSources.value.includes(url)),
)

watch(
  () => [props.picture.id, props.picture.thumbnailUrl, props.picture.url],
  () => {
    failedSources.value = []
    previewOpen.value = false
  },
)

function handleLoad(event: Event) {
  loadedSrc.value = (event.target as HTMLImageElement).getAttribute('src') || ''
}

function handleError(event: Event) {
  // Ignore a late error from an image that has already been replaced.
  const failedUrl = (event.target as HTMLImageElement).getAttribute('src')
  if (failedUrl && failedUrl === src.value) failedSources.value.push(failedUrl)
}

function setPreviewOpen(visible: boolean) {
  previewOpen.value = visible
}
</script>

<style scoped>
.picture-thumbnail {
  position: relative;
  max-width: 100%;
  overflow: hidden;
  background: #f5f5f5;
}

.picture-thumbnail > img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0;
  transition: opacity 0.45s ease;
}
.picture-thumbnail > img.is-loaded {
  opacity: 1;
}
/* 加载中显示一层很淡的流光骨架，图片到达后淡入 */
.picture-thumbnail.is-loading {
  background: linear-gradient(100deg, #f2f2f4 30%, #e9e9ed 50%, #f2f2f4 70%) 0 0 / 300% 100%;
  animation: thumbnail-shimmer 1.6s ease-in-out infinite;
}
@keyframes thumbnail-shimmer {
  from {
    background-position: 100% 0;
  }
  to {
    background-position: 0 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .picture-thumbnail.is-loading {
    animation: none;
  }
  .picture-thumbnail > img {
    transition: none;
  }
}

.image-unavailable {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  color: #777;
  font-size: 12px;
}

.preview-trigger {
  position: absolute;
  inset: 0;
  width: 100%;
  border: 0;
  padding: 0;
  background: transparent;
  cursor: zoom-in;
}

.preview-trigger:focus-visible {
  outline: 2px solid #1677ff;
  outline-offset: -2px;
}
</style>
