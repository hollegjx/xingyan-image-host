<template>
  <div class="favorites-page" :aria-busy="loading">
    <PageHeader
      title="我的收藏"
      eyebrow="珍藏 · 再次相遇"
      description="这里保留你收藏的公开作品。已删除、下架或转为私有的图片不再展示。"
    />
    <a-alert v-if="error" type="error" :message="error" show-icon>
      <template #action><a-button @click="load">重新加载</a-button></template>
    </a-alert>
    <a-skeleton v-else-if="loading" active />
    <a-empty v-else-if="!pictures.length" description="还没有可展示的收藏，去公共图库看看吧">
      <a-button type="primary" @click="router.push('/')">浏览公共图库</a-button>
    </a-empty>
    <div v-else class="favorites-grid">
      <article v-for="picture in pictures" :key="picture.id" class="favorite-card">
        <router-link
          :to="`/picture/${picture.id}`"
          :aria-label="`查看 ${picture.name || '未命名图片'}`"
        >
          <PictureThumbnail :picture="picture" :height="220" />
          <h2>{{ picture.name || '未命名图片' }}</h2>
        </router-link>
        <a-button
          :loading="removing === String(picture.id)"
          :disabled="!!removing"
          @click="remove(picture)"
          ><HeartFilled /> 取消收藏</a-button
        >
      </article>
    </div>
    <a-pagination
      v-if="!error && total > pageSize"
      :current="current"
      :page-size="pageSize"
      :total="total"
      :show-size-changer="false"
      @change="changePage"
    />
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { HeartFilled } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import PageHeader from '@/components/PageHeader.vue'
import PictureThumbnail from '@/components/PictureThumbnail.vue'
import { listMyFavorites, setPictureFavorite } from '@/service/api/pictureFavoriteController'
const router = useRouter()
const pictures = ref<API.PictureVO[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = 12
const loading = ref(false)
const error = ref('')
const removing = ref('')
let sequence = 0
const load = async () => {
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  pictures.value = []
  try {
    const result = await listMyFavorites(current.value, pageSize)
    if (ticket !== sequence) return
    if (result.code !== 0 || !result.data) throw new Error(result.message || '获取收藏失败')
    pictures.value = result.data.records || []
    total.value = Number(result.data.total || 0)
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize))
    if (current.value > lastPage) {
      current.value = lastPage
      await load()
    }
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '获取收藏失败，请重试'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}
const changePage = (page: number) => {
  current.value = page
  void load()
}
const remove = async (picture: API.PictureVO) => {
  if (!picture.id || removing.value) return
  removing.value = String(picture.id)
  const ticket = sequence
  try {
    const result = await setPictureFavorite(picture.id, false)
    if (ticket !== sequence) return
    if (result.code !== 0) throw new Error(result.message || '取消收藏失败')
    await load()
  } catch (e) {
    if (ticket === sequence) message.error(e instanceof Error ? e.message : '取消收藏失败，请重试')
  } finally {
    removing.value = ''
  }
}
onMounted(load)
onBeforeUnmount(() => {
  sequence++
})
</script>
<style scoped>
.favorites-page {
  max-width: 1280px;
  margin: 0 auto;
}
.favorites-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 260px), 1fr));
  gap: 24px;
  margin-bottom: 28px;
}
.favorite-card {
  padding: 16px;
  border: 1px solid var(--border);
  border-radius: 20px;
  background: var(--surface);
}
.favorite-card h2 {
  color: var(--ink);
  font-size: 17px;
  overflow-wrap: anywhere;
  margin: 16px 0;
}
.favorite-card a {
  text-decoration: none;
}
</style>
