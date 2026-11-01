<template>
  <div v-if="canSearchPublicPicture(picture) && !hideWhenDisabled" class="search-action">
  <a-button
    :disabled="!capabilities.externalImageSearchEnabled"
    :title="
      capabilityError ||
      (!capabilities.externalImageSearchEnabled ? '外部以图搜图尚未启用' : '以图搜图')
    "
    @click.stop.prevent="openSearch"
    >以图搜图</a-button
  >
  <span v-if="!capabilities.externalImageSearchEnabled" class="search-note">{{ capabilityError || '以图搜图暂未开放' }}</span>
  </div>
  <a-button v-else-if="canSearchPublicPicture(picture) && capabilities.externalImageSearchEnabled" @click.stop.prevent="openSearch">以图搜图</a-button>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { canSearchPublicPicture } from '@/utils/imageSearch'
import { usePictureCapabilities } from '@/composables/usePictureCapabilities'
const { capabilities, capabilityError } = usePictureCapabilities()
// 列表卡片里不展示不可用的入口，避免每张卡片都挂一个灰色按钮
const props = defineProps<{ picture: API.PictureVO; hideWhenDisabled?: boolean }>()
const router = useRouter()
const openSearch = () =>
  router.push({ path: '/picture/search', query: { pictureId: String(props.picture.id) } })
</script>

<style scoped>
.search-action { display: inline-flex; flex-direction: column; gap: 5px; }
.search-note { color: var(--muted); font-size: 11px; }
</style>
