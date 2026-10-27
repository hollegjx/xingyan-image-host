<template>
  <a-button v-if="canSearchPublicPicture(picture)" @click.stop.prevent="openSearch"
    >以图搜图</a-button
  >
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { canSearchPublicPicture } from '@/utils/imageSearch'
const props = defineProps<{ picture: API.PictureVO }>()
const router = useRouter()
const openSearch = () =>
  router.push({ path: '/picture/search', query: { pictureId: String(props.picture.id) } })
</script>
