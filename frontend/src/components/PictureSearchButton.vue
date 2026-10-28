<template>
  <a-button v-if="canSearchPublicPicture(picture)" :disabled="!capabilities.externalImageSearchEnabled"
    :title="capabilityError || (!capabilities.externalImageSearchEnabled ? '外部以图搜图尚未启用' : '以图搜图')" @click.stop.prevent="openSearch"
    >以图搜图</a-button
  >
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { canSearchPublicPicture } from '@/utils/imageSearch'
import { usePictureCapabilities } from '@/composables/usePictureCapabilities'
const { capabilities, capabilityError } = usePictureCapabilities()
const props = defineProps<{ picture: API.PictureVO }>()
const router = useRouter()
const openSearch = () =>
  router.push({ path: '/picture/search', query: { pictureId: String(props.picture.id) } })
</script>
