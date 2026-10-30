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
import { ArrowUpOutlined, ExportOutlined } from '@ant-design/icons-vue'
import PictureThumbnail from '@/components/PictureThumbnail.vue'
import PictureSearchButton from '@/components/PictureSearchButton.vue'
defineProps<{ picture: API.PictureVO }>()
defineEmits<{ share: [picture: API.PictureVO] }>()
</script>
<style scoped>
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
  font-size: 9px;
  color: var(--muted);
  letter-spacing: 0.03em;
  flex-shrink: 0;
}
.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 25px;
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
  font-size: 9px;
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
