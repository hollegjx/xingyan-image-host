<template>
  <main id="searchPicturePage">
    <h2>以图搜图</h2>
    <p>使用公开图片在百度识图中查找相似图片，不是搜索本站私人图库。</p>
    <a-spin v-if="loading" tip="正在读取原图信息" />
    <a-alert v-else-if="loadError" type="error" :message="loadError" show-icon>
      <template #action><a-button @click="loadPicture">重试加载</a-button></template>
    </a-alert>
    <template v-else-if="picture.id">
      <section class="source">
        <img
          v-if="picture.url"
          :src="picture.thumbnailUrl || picture.url"
          :alt="picture.name || '待搜索原图'"
        />
        <div>
          <h3>{{ picture.name || '未命名图片' }}</h3>
          <router-link :to="`/picture/${picture.id}`">查看图片详情</router-link>
        </div>
      </section>
      <a-alert
        v-if="!searchable"
        type="warning"
        show-icon
        message="仅支持已审核通过的公开图片"
        description="私人图片、草稿和待审核图片不会发送给第三方。"
      />
      <a-alert v-else-if="!externalEnabled" type="info" show-icon message="外部以图搜图尚未启用，请等待管理员完成服务配置与验收。" />
      <template v-else>
        <a-alert
          type="info"
          show-icon
          message="搜索前请确认"
          description="搜索会把此公开图片的地址发送给百度，百度可能下载并处理图片；展示结果时浏览器会加载第三方缩略图。取消勾选不会撤回已经发送的请求。"
        />
        <div class="controls">
          <a-checkbox v-model:checked="consent" :disabled="searching"
            >我同意将此公开图片地址发送给百度识图</a-checkbox
          >
          <a-button type="primary" :disabled="!consent" :loading="searching" @click="runSearch"
            >开始搜索</a-button
          >
        </div>
        <p class="hint">第三方接口可能暂不可用；请勿连续重复请求，每次搜索至少间隔 10 秒。</p>
        <a-alert v-if="searchError" type="error" :message="searchError" show-icon />
        <a-empty
          v-else-if="searched && !searching && !results.length"
          description="未找到相似图片"
        />
        <section v-if="results.length" aria-label="相似图片结果" class="results">
          <a
            v-for="(item, index) in results"
            :key="`${item.thumbUrl}-${item.fromUrl}`"
            :href="item.fromUrl"
            target="_blank"
            rel="noopener noreferrer"
            referrerpolicy="no-referrer"
          >
            <img
              :src="item.thumbUrl"
              :alt="`相似图片 ${index + 1}`"
              loading="lazy"
              decoding="async"
              referrerpolicy="no-referrer"
            />
            <span>查看来源 {{ index + 1 }}（第三方网站）</span>
          </a>
        </section>
      </template>
    </template>
  </main>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { getPictureVoByIdUsingGet } from '@/service/api/pictureController'
import { searchPictureByPictureUsingPost } from '@/service/api/pictureSearchController'
import { canSearchPublicPicture, safeImageSearchResults } from '@/utils/imageSearch'
import { parseId, sameId } from '@/utils/space'
import { loadPictureCapabilities } from '@/composables/usePictureCapabilities'
const externalEnabled = ref(false)

const route = useRoute()
const picture = ref<API.PictureVO>({})
const results = ref<API.ImageSearchResult[]>([])
const loading = ref(false)
const searching = ref(false)
const consent = ref(false)
const searched = ref(false)
const loadError = ref('')
const searchError = ref('')
const searchable = computed(() => canSearchPublicPicture(picture.value))
let sequence = 0
let controller: AbortController | undefined

const loadPicture = async () => {
  const ticket = ++sequence
  controller?.abort()
  controller = new AbortController()
  picture.value = {}
  results.value = []
  consent.value = false
  searched.value = false
  searching.value = false
  loadError.value = ''
  searchError.value = ''
  loading.value = true
  const id = parseId(route.query.pictureId)
  try {
    if (!id) throw new Error('图片 ID 无效')
    const response = await getPictureVoByIdUsingGet({ id }, { signal: controller.signal })
    if (ticket !== sequence) return
    if (response.code !== 0 || !response.data || !sameId(response.data.id, id))
      throw new Error(response.message || '读取图片失败')
    picture.value = response.data
    const capabilities = await loadPictureCapabilities()
    if (ticket !== sequence) return
    externalEnabled.value = capabilities.externalImageSearchEnabled
  } catch (error) {
    if (ticket === sequence)
      loadError.value = error instanceof Error ? error.message : '读取图片失败'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}

const runSearch = async () => {
  if (!externalEnabled.value || !consent.value || !searchable.value || searching.value) return
  const ticket = sequence
  const id = parseId(picture.value.id)
  if (!id) return
  searching.value = true
  searched.value = false
  searchError.value = ''
  results.value = []
  try {
    const response = await searchPictureByPictureUsingPost(
      { pictureId: id, externalSearchConsent: true },
      { timeout: 25000, signal: controller?.signal },
    )
    if (ticket !== sequence) return
    if (response.code !== 0 || !Array.isArray(response.data))
      throw new Error(response.message || '搜索暂时不可用')
    const safe = safeImageSearchResults(response.data)
    if (response.data.length && !safe.length) throw new Error('第三方结果格式异常，请稍后再试')
    results.value = safe
    searched.value = true
  } catch (error) {
    if (ticket === sequence)
      searchError.value = error instanceof Error ? error.message : '搜索失败，请稍后再试'
  } finally {
    if (ticket === sequence) searching.value = false
  }
}

watch(() => route.query.pictureId, loadPicture, { immediate: true })
onBeforeUnmount(() => {
  sequence++
  controller?.abort()
})
</script>

<style scoped>
#searchPicturePage {
  padding: 24px;
}
.source {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin: 20px 0;
}
.source img {
  width: 220px;
  max-width: 100%;
  height: 160px;
  object-fit: contain;
}
.source h3 {
  overflow-wrap: anywhere;
}
.controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
}
.hint {
  margin: 12px 0 20px;
}
.results {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 200px), 1fr));
  gap: 16px;
  margin-top: 20px;
}
.results a {
  min-width: 0;
}
.results img {
  display: block;
  width: 100%;
  height: 180px;
  object-fit: contain;
}
.results span {
  display: block;
  padding: 8px 0;
}
@media (max-width: 480px) {
  #searchPicturePage {
    padding: 12px;
  }
  .source {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
