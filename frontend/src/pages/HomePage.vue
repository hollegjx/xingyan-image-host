<template>
  <div id="homePage" :aria-busy="loading">
    <section class="discovery-hero">
      <div class="hero-copy">
        <EaveMotif class="hero-eave" />
        <span class="eyebrow">星檐图床 · 一处安静的图片居所</span>
        <h1>为每一张图片，<br /><span>留一处屋檐。</span></h1>
        <p>收纳日常的画面，也分享偶然的光。</p>
        <div class="hero-actions">
          <router-link to="/space/mine" class="hero-primary"
            >收好我的图片 <ArrowRightOutlined
          /></router-link>
          <router-link :to="{ path: '/', hash: '#gallery' }" class="hero-secondary"
            >逛逛公共图库 <span aria-hidden="true">↓</span></router-link
          >
        </div>
      </div>
    </section>
    <section id="gallery" class="discovery-library" aria-label="公共图库">
      <div class="library-heading">
        <div>
          <h2>探索灵感</h2>
          <span>檐下相遇的好画面。</span>
        </div>
        <span class="image-count">{{ total.toLocaleString() }} <span>张图片</span></span>
      </div>
      <div class="library-controls">
        <div class="category-tabs" role="group" aria-label="分类筛选">
          <button
            type="button"
            :aria-pressed="!searchParams.category"
            :class="{ selected: !searchParams.category }"
            @click="onCategoryChange(undefined)"
          >
            全部
          </button>
          <button
            v-for="category in categoryList"
            :key="category"
            type="button"
            :aria-pressed="searchParams.category === category"
            :class="{ selected: searchParams.category === category }"
            @click="onCategoryChange(category)"
          >
            {{ category }}
          </button>
        </div>
        <form id="search-bar" role="search" @submit.prevent="doSearch">
          <SearchOutlined /><input
            v-model="searchParams.searchText"
            aria-label="搜索图片"
            placeholder="搜索你的灵感..."
          />
          <button type="submit" aria-label="搜索"><ArrowRightOutlined /></button>
        </form>
      </div>
      <div class="library-tools">
        <div class="filter-tools">
          <button
            class="tag-toggle"
            type="button"
            :aria-expanded="tagsOpen"
            @click="tagsOpen = !tagsOpen"
          >
            <FilterOutlined /> 标签筛选
            <span v-if="selectedTags.length" class="tag-count">{{ selectedTags.length }}</span
            ><DownOutlined />
          </button>
          <button v-if="hasFilters" class="clear-filters" type="button" @click="clearFilters">
            清除筛选 <CloseOutlined />
          </button>
        </div>
        <div class="view-switch" role="group" aria-label="图片布局">
          <button
            type="button"
            aria-label="舒适布局"
            :aria-pressed="!compact"
            :class="{ active: !compact }"
            @click="compact = false"
          >
            <AppstoreOutlined />
          </button>
          <button
            type="button"
            aria-label="紧凑布局"
            :aria-pressed="compact"
            :class="{ active: compact }"
            @click="compact = true"
          >
            <TableOutlined />
          </button>
        </div>
      </div>
      <div v-if="tagsOpen" class="tag-panel" role="group" aria-label="标签">
        <button
          type="button"
          :aria-pressed="selectedTags.length === 0"
          :class="{ selected: !selectedTags.length }"
          @click="onTagClear"
        >
          全部标签
        </button>
        <button
          v-for="tag in tagList"
          :key="tag"
          type="button"
          :aria-pressed="selectedTags.includes(tag)"
          :class="{ selected: selectedTags.includes(tag) }"
          @click="onTagChange(tag)"
        >
          # {{ tag }}
        </button>
        <span v-if="!tagList.length" class="muted">暂无可用标签</span>
      </div>
      <a-alert v-if="error" type="error" :message="error" show-icon class="load-error"
        ><template #action
          ><a-button aria-label="重试" @click="fetchData">重试</a-button></template
        ></a-alert
      >
      <div
        v-else-if="loading"
        class="picture-grid"
        :class="{ compact }"
        aria-label="图片加载中"
        aria-busy="true"
      >
        <div v-for="n in 8" :key="n" class="skeleton-card">
          <div />
          <span /><span />
        </div>
      </div>
      <div v-else-if="dataList.length" class="picture-grid" :class="{ compact }">
        <GalleryCard
          v-for="picture in dataList"
          :key="String(picture.id)"
          :picture="picture"
          @share="openShare"
        />
      </div>
      <div v-else class="gallery-empty">
        <div class="empty-icon"><BrandMark /></div>
        <h3>{{ hasFilters ? '还没有找到这份灵感' : '屋檐已备好，等第一张图片。' }}</h3>
        <p>
          {{
            hasFilters
              ? '试试其他关键词，或者换一个分类。'
              : '这里还没有公开图片，上传你的第一张作品吧。'
          }}
        </p>
        <a-button v-if="hasFilters" @click="clearFilters">清除筛选</a-button>
        <router-link v-else to="/picture/add" class="empty-upload"
          >上传第一张图片 <ArrowRightOutlined
        /></router-link>
      </div>
      <div v-if="total" class="pagination-wrapper">
        <span>一张一张，收好日常。</span
        ><a-pagination
          :current="searchParams.current"
          :page-size="searchParams.pageSize"
          :total="total"
          show-size-changer
          :page-size-options="['12', '16', '20']"
          @change="onPageChange"
        />
      </div>
    </section>
    <PictureShareModal v-model:open="shareOpen" :picture="sharePicture" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, reactive, computed } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useNavigationStore } from '@/stores/useNavigationStore'
import {
  SearchOutlined,
  ArrowRightOutlined,
  FilterOutlined,
  DownOutlined,
  CloseOutlined,
  AppstoreOutlined,
  TableOutlined,
} from '@ant-design/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import EaveMotif from '@/components/EaveMotif.vue'
import GalleryCard from '@/components/GalleryCard.vue'
import PictureShareModal from '@/components/PictureShareModal.vue'
import {
  listPictureVoByPageUsingPost,
  listPictureTagCategoryUsingGet,
} from '@/service/api/pictureController'

const navigation = useNavigationStore()
type HomeView = {
  search: API.PictureQueryRequest
  selectedTags: string[]
  tagsOpen: boolean
  compact: boolean
}
const restored = navigation.readList<HomeView>('home')
const dataList = ref<API.PictureVO[]>([]),
  total = ref(0),
  loading = ref(true),
  error = ref('')
const shareOpen = ref(false),
  sharePicture = ref<API.PictureVO>()
const categoryList = ref<string[]>([]),
  tagList = ref<string[]>([]),
  selectedTags = ref<string[]>(restored?.selectedTags || [])
const tagsOpen = ref(restored?.tagsOpen || false),
  compact = ref(restored?.compact || false)
const searchParams = reactive<API.PictureQueryRequest>(
  restored?.search || { current: 1, pageSize: 12 },
)
onBeforeRouteLeave(() => {
  navigation.rememberList('home', {
    search: searchParams,
    selectedTags: selectedTags.value,
    tagsOpen: tagsOpen.value,
    compact: compact.value,
  })
})
const hasFilters = computed(
  () =>
    !!searchParams.category || selectedTags.value.length > 0 || !!searchParams.searchText?.trim(),
)
let sequence = 0,
  disposed = false
function openShare(picture: API.PictureVO) {
  sharePicture.value = picture
  shareOpen.value = true
}
async function fetchTagCategory() {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (!disposed && res.code === 0 && res.data) {
      categoryList.value = res.data.categoryList || []
      tagList.value = res.data.tagList || []
    }
  } catch {
    /* A tag-service failure does not hide the independently loaded gallery. */
  }
}
async function fetchData() {
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  try {
    const res = await listPictureVoByPageUsingPost({
      ...searchParams,
      tags: searchParams.tags ? [...searchParams.tags] : undefined,
    })
    if (ticket !== sequence) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '无法加载图片，请稍后重试')
    dataList.value = res.data.records || []
    total.value = Number(res.data.total || 0)
    const lastPage = Math.max(1, Math.ceil(total.value / (searchParams.pageSize || 12)))
    if ((searchParams.current || 1) > lastPage) {
      searchParams.current = lastPage
      await fetchData()
    }
  } catch (e) {
    if (ticket !== sequence) return
    dataList.value = []
    total.value = 0
    error.value = e instanceof Error ? e.message : '无法加载图片，请检查网络后重试'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}
function doSearch() {
  searchParams.current = 1
  void fetchData()
}
function onCategoryChange(category?: string) {
  searchParams.category = category
  doSearch()
}
function onTagChange(tag: string) {
  selectedTags.value = selectedTags.value.includes(tag)
    ? selectedTags.value.filter((item) => item !== tag)
    : [...selectedTags.value, tag]
  searchParams.tags = selectedTags.value.length ? [...selectedTags.value] : undefined
  doSearch()
}
function onTagClear() {
  selectedTags.value = []
  searchParams.tags = undefined
  doSearch()
}
function clearFilters() {
  selectedTags.value = []
  searchParams.tags = undefined
  searchParams.category = undefined
  searchParams.searchText = ''
  doSearch()
}
function onPageChange(page: number, pageSize: number) {
  searchParams.current = searchParams.pageSize !== pageSize ? 1 : page
  searchParams.pageSize = pageSize
  void fetchData()
}
onMounted(() => {
  void fetchTagCategory()
  void fetchData()
})
onBeforeUnmount(() => {
  sequence++
  disposed = true
})
</script>

<style scoped>
.discovery-hero {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  padding: 6px 0 52px;
  margin-bottom: 26px;
  text-align: center;
}
.hero-copy {
  max-width: 100%;
}
.hero-eave {
  display: block;
  margin: 0 auto 17px;
}
.hero-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 26px;
  margin-top: 27px;
  font-size: 12px;
}
.hero-primary {
  display: inline-flex;
  align-items: center;
  gap: 19px;
  border-radius: 100px;
  background: var(--ink);
  color: white;
  padding: 12px 22px;
}
.hero-secondary {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: var(--muted);
}
.hero-secondary:hover {
  color: var(--ink);
}
.discovery-library {
  scroll-margin-top: 90px;
}
.hero-copy .eyebrow {
  font-size: 13px;
  letter-spacing: 0.08em;
  color: var(--muted);
}
.hero-copy h1 {
  font-size: clamp(38px, 4vw, 58px);
  line-height: 1.24;
  margin: 19px 0 20px;
  font-weight: 650;
  letter-spacing: -0.055em;
}
.hero-copy h1 > span {
  color: var(--ink);
}
.hero-copy p {
  margin: 0;
  font-size: 14px;
  color: var(--muted);
}
.library-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 27px;
}
.library-heading > div {
  display: flex;
  align-items: baseline;
  gap: 17px;
}
.library-heading h2 {
  margin: 0;
  font-size: 22px;
  letter-spacing: -0.045em;
}
.library-heading > div > span {
  color: var(--muted);
  font-size: 12px;
}
.image-count {
  font-size: 13px;
  color: var(--ink);
}
.image-count > span {
  font-size: 11px;
  margin-left: 5px;
  color: var(--muted);
}
.library-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}
.category-tabs {
  display: flex;
  gap: 8px;
  align-items: center;
  overflow-x: auto;
  padding: 4px 2px;
  margin: -4px -2px;
}
.category-tabs button {
  white-space: nowrap;
  background: transparent;
  border: 0;
  padding: 10px 17px;
  font-size: 12px;
  color: var(--muted);
  border-radius: 100px;
  cursor: pointer;
  transition:
    background 0.18s,
    color 0.18s;
}
.category-tabs button.selected {
  color: white;
  background: var(--ink);
}
.category-tabs button:hover:not(.selected) {
  background: var(--surface-soft);
  color: var(--ink);
}
#search-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  width: 264px;
  gap: 9px;
  height: 40px;
  border: 1px solid transparent;
  border-radius: 100px;
  padding: 0 15px;
  background: var(--surface-soft);
  color: var(--muted);
}
#search-bar:focus-within {
  border-color: #86868b;
  box-shadow: 0 0 0 3px #1d1d1f0a;
}
#search-bar input {
  width: 100%;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  font-size: 12px;
  color: var(--ink);
}
#search-bar input::placeholder {
  color: var(--muted);
}
#search-bar button {
  border: 0;
  padding: 3px;
  background: transparent;
  color: var(--ink);
  cursor: pointer;
}
.library-tools {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 0;
  margin-bottom: 2px;
}
.filter-tools {
  display: flex;
  align-items: center;
  gap: 18px;
}
.tag-toggle,
.clear-filters {
  border: 0;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  cursor: pointer;
  padding: 3px 0;
  display: flex;
  align-items: center;
  gap: 7px;
}
.tag-toggle .anticon:last-child {
  font-size: 8px;
}
.tag-count {
  background: #ebebee;
  color: var(--ink);
  padding: 0 5px;
  border-radius: 3px;
}
.view-switch {
  display: flex;
  gap: 3px;
  padding: 3px;
  background: var(--surface-soft);
  border-radius: 9px;
}
.view-switch button {
  width: 28px;
  height: 26px;
  padding: 0;
  display: grid;
  place-items: center;
  background: transparent;
  border: 0;
  border-radius: 6px;
  color: var(--muted);
  cursor: pointer;
}
.view-switch button.active {
  background: white;
  color: var(--ink);
  box-shadow: 0 1px 3px #00000012;
}
.tag-panel {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  padding: 14px;
  margin-bottom: 22px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: white;
}
.tag-panel button {
  font-size: 11px;
  padding: 5px 9px;
  background: var(--surface-soft);
  border: 1px solid transparent;
  border-radius: 100px;
  color: var(--muted);
  cursor: pointer;
}
.tag-panel button.selected {
  border-color: var(--ink);
  color: var(--ink);
  background: white;
}
.muted {
  color: var(--muted);
  font-size: 12px;
}
.picture-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 32px 24px;
}
.picture-grid.compact {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}
.picture-grid.compact :deep(.picture-thumbnail) {
  height: 170px !important;
}
.skeleton-card > div {
  height: 220px;
  background: var(--surface-soft);
  border-radius: 16px;
}
.skeleton-card > span {
  display: block;
  margin-top: 13px;
  width: 60%;
  height: 10px;
  background: var(--surface-soft);
  border-radius: 3px;
}
.skeleton-card > span:last-child {
  width: 35%;
  height: 7px;
}
.gallery-empty {
  min-height: 330px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: #fafafa;
  padding: 32px;
  text-align: center;
}
.empty-icon {
  position: relative;
  width: 64px;
  height: 64px;
  display: grid;
  place-items: center;
  background: #ededf0;
  border-radius: 18px;
  color: var(--muted);
  font-size: 28px;
  margin-bottom: 20px;
}
.empty-icon > svg {
  width: 36px;
  height: 36px;
}
.gallery-empty h3 {
  font-size: 17px;
  margin: 0 0 8px;
  font-weight: 550;
}
.gallery-empty p {
  font-size: 12px;
  color: var(--muted);
  margin: 0 0 22px;
}
.empty-upload {
  display: flex;
  gap: 16px;
  align-items: center;
  color: var(--ink);
  font-size: 12px;
}
.pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 20px;
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid var(--line);
}
.pagination-wrapper > span {
  color: var(--muted);
  font-size: 11px;
}
@media (min-width: 1700px) {
  .picture-grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}
@media (max-width: 1200px) {
  .picture-grid,
  .picture-grid.compact {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .library-controls {
    flex-wrap: wrap;
    gap: 14px;
  }
  #search-bar {
    width: 100%;
  }
}
@media (max-width: 800px) {
  .picture-grid,
  .picture-grid.compact {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .discovery-hero {
    min-height: 280px;
    padding: 28px 0 46px;
  }
}
@media (max-width: 540px) {
  .discovery-hero {
    min-height: 0;
    padding: 24px 0 42px;
    margin-bottom: 24px;
  }
  .hero-copy h1 {
    font-size: clamp(30px, 9vw, 40px);
    margin: 16px 0;
  }
  .hero-copy .eyebrow {
    font-size: 12px;
  }
  .hero-copy p {
    font-size: 12px;
  }
  .hero-eave {
    width: 88px;
    height: 29px;
    margin-bottom: 13px;
  }
  .hero-actions {
    gap: 18px;
    margin-top: 23px;
  }
  .library-heading > div > span {
    display: none;
  }
  .library-heading h2 {
    font-size: 20px;
  }
  .category-tabs {
    width: 100%;
  }
  .category-tabs button {
    padding: 9px 14px;
    font-size: 12px;
  }
  .picture-grid,
  .picture-grid.compact {
    gap: 22px 13px;
  }
  .pagination-wrapper {
    flex-direction: column-reverse;
    align-items: center;
  }
  .pagination-wrapper :deep(.ant-pagination-options) {
    display: none;
  }
}
@media (max-width: 360px) {
  .picture-grid,
  .picture-grid.compact {
    grid-template-columns: 1fr;
  }
}
</style>
