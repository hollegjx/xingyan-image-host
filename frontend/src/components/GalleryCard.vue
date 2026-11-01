<template>
  <article class="gallery-card">
    <router-link
      :to="`/picture/${picture.id}`"
      class="card-image"
      :aria-label="`查看 ${picture.name || '未命名图片'}`"
    >
      <PictureThumbnail :picture="picture" :height="220" />
      <span v-if="picture.category" class="image-category">{{ picture.category }}</span>
      <span class="view-image"><ArrowUpOutlined /></span>
    </router-link>
    <div class="card-caption">
      <router-link :to="`/picture/${picture.id}`" class="card-name">{{
        picture.name || '未命名图片'
      }}</router-link>
      <span class="card-format">{{ picture.picFormat?.toUpperCase() || 'IMAGE' }}</span>
    </div>
    <div class="card-bottom">
      <span class="card-author"
        ><span class="author-dot">{{ picture.user?.userName?.slice(0, 1) || '云' }}</span
        >{{ picture.user?.userName || '星檐图床' }}</span
      >
      <div class="card-actions">
        <button type="button" class="favorite-button" :disabled="favoriteBusy || (!!user.loginUser.id && !status)"
          :aria-pressed="!!status?.favorited" :aria-label="`${status?.favorited ? '取消收藏' : '收藏'} ${picture.name || '未命名图片'}`"
          @click="toggleFavorite"><HeartFilled v-if="status?.favorited" aria-hidden="true" /><HeartOutlined v-else aria-hidden="true" />
          <span>{{ status?.favorited ? '已收藏' : '收藏' }} · {{ status?.favoriteCount ?? '—' }}</span></button>
        <PictureSearchButton :picture="picture" hide-when-disabled /><button
          type="button"
          aria-label="分享"
          @click="$emit('share', picture)"
        >
          <ExportOutlined />
        </button>
      </div>
    </div>
  </article>
</template>
<script setup lang="ts">
import { ArrowUpOutlined, ExportOutlined, HeartOutlined, HeartFilled } from '@ant-design/icons-vue'
import PictureThumbnail from '@/components/PictureThumbnail.vue'
import PictureSearchButton from '@/components/PictureSearchButton.vue'
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { setPictureFavorite, type FavoriteStatus } from '@/service/api/pictureFavoriteController'
const props = defineProps<{ picture: API.PictureVO; favoriteStatus?: FavoriteStatus }>()
const user = useLoginUserStore()
const router = useRouter(), route = useRoute()
const status = ref<FavoriteStatus>()
const favoriteBusy = ref(false)
let version = 0
watch(() => [props.picture.id, props.favoriteStatus, user.loginUser.id], () => {
  version++
  favoriteBusy.value = false
  status.value = props.favoriteStatus ? { ...props.favoriteStatus, favorited: !!user.loginUser.id && props.favoriteStatus.favorited } : undefined
}, { immediate: true })
onBeforeUnmount(() => { version++ })
async function toggleFavorite() {
  if (favoriteBusy.value || props.picture.id == null) return
  if (!user.loginUser.id) {
    await router.push({ path: '/user/login', query: { redirect: route.fullPath } })
    return
  }
  if (!status.value) return
  const ticket = version
  favoriteBusy.value = true
  try {
    const res = await setPictureFavorite(props.picture.id, !status.value.favorited)
    if (ticket !== version) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '收藏操作失败，请重试')
    status.value = res.data
    message.success(res.data.favorited ? '已收藏' : '已取消收藏')
  } catch (error) {
    if (ticket === version) message.error(error instanceof Error ? error.message : '收藏操作失败，请重试')
  } finally {
    if (ticket === version) favoriteBusy.value = false
  }
}
defineEmits<{ share: [picture: API.PictureVO] }>()
</script>
<style scoped>
.favorite-button { display: inline-flex; align-items: center; gap: 4px; font-size: 11px; }
.favorite-button[aria-pressed="true"] { color: #9c5260; }
.card-actions > button:disabled { cursor: wait; opacity: .5; }
.gallery-card {
  min-width: 0;
}
.card-image {
  position: relative;
  display: block;
  border-radius: 16px;
  overflow: hidden;
  background: var(--surface-soft);
  isolation: isolate;
}
.card-image :deep(img) {
  transition: transform 0.35s ease;
}
.card-image:hover :deep(img) {
  transform: scale(1.035);
}
.image-category {
  position: absolute;
  left: 12px;
  top: 12px;
  background: #ffffffeb;
  padding: 5px 10px;
  border-radius: 100px;
  font-size: 10px;
  color: #515154;
}
.view-image {
  position: absolute;
  right: 12px;
  bottom: 12px;
  display: grid;
  place-items: center;
  width: 29px;
  height: 29px;
  border-radius: 50%;
  background: white;
  color: var(--accent);
  opacity: 0;
  transform: translateY(4px);
  transition: 0.2s;
}
.view-image .anticon {
  transform: rotate(45deg);
}
.card-image:hover .view-image,
.card-image:focus-visible .view-image {
  opacity: 1;
  transform: translateY(0);
}
.card-caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 13px 0 6px;
}
.card-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--ink);
}
.card-format {
  font-size: 10px;
  color: var(--muted);
  letter-spacing: 0.03em;
  flex-shrink: 0;
}
.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 25px;
  flex-wrap: wrap;
  gap: 8px;
}
.card-author {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--muted);
  font-size: 11px;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.author-dot {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  background: #ededf0;
  border-radius: 50%;
  font-size: 10px;
  color: var(--muted);
}
.card-actions {
  display: flex;
  gap: 9px;
  align-items: center;
  flex-shrink: 0;
}
.card-actions > button {
  background: transparent;
  border: 0;
  color: var(--muted);
  padding: 3px;
  cursor: pointer;
}
.card-actions :deep(.ant-btn) {
  height: 23px;
  padding: 0;
  border: 0;
  background: transparent;
  box-shadow: none;
  color: var(--muted);
  font-size: 11px;
}
@media (max-width: 500px) {
  .card-image :deep(.picture-thumbnail) {
    height: 180px !important;
  }
  .card-author {
    max-width: 75px;
  }
}
</style>
