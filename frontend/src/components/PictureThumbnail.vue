<template>
  <div class="picture-thumbnail" :style="{ width, height: `${height}px` }">
    <img
      v-if="src"
      :key="src"
      :src="src"
      :alt="picture.name || '图片'"
      loading="lazy"
      decoding="async"
      @error="handleError"
    />
    <span v-else class="image-unavailable" role="img" :aria-label="`${picture.name || '图片'}：加载失败`">
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
