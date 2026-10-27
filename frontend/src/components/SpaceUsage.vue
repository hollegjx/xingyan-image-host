<template>
  <div class="space-usage">
    <div>
      <span>图片数量：{{ space.totalCount ?? 0 }} / {{ space.maxCount ?? '—' }} 张</span>
      <a-progress :percent="usagePercent(space.totalCount, space.maxCount)" :show-info="false" />
    </div>
    <div>
      <span>已用容量：{{ formatBytes(space.totalSize) }} / {{ formatBytes(space.maxSize) }}</span>
      <a-progress :percent="usagePercent(space.totalSize, space.maxSize)" :show-info="false" />
    </div>
    <p>按原图大小计量，草稿也占额度；缩略图和展示图不重复计入。</p>
  </div>
</template>

<script setup lang="ts">
import { formatBytes, usagePercent } from '@/utils/space'
defineProps<{ space: API.SpaceVO }>()
</script>

<style scoped>
.space-usage {
  margin: 16px 0;
  max-width: 640px;
}
.space-usage p {
  color: #666;
  margin-bottom: 0;
}
</style>
