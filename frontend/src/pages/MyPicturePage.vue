<template>
  <div id="myPicturePage" class="submissions-page" :aria-busy="loading">
    <header class="submissions-header">
      <div>
        <span class="eyebrow">创作 · 分享 · 被看见</span>
        <h1>我的投稿<span class="title-period">。</span></h1>
        <p>分享给公共图库的作品，都在这里。</p>
      </div>
      <router-link to="/picture/add" class="submission-create"
        ><PlusOutlined /> 发布新作品 <ArrowRightOutlined
      /></router-link>
    </header>
    <div class="submission-context">
      <span><GlobalOutlined /> 这里管理公开投稿，草稿与未通过审核的作品不会公开。</span>
      <router-link to="/space/mine">寻找私人图片？去我的空间 <ArrowUpOutlined /></router-link>
    </div>
    <section class="submissions-library" aria-label="投稿作品">
      <div class="submission-toolbar">
        <div class="submission-filters" role="group" aria-label="投稿状态筛选">
          <button
            v-for="item in submissionFilters"
            :key="item.key"
            type="button"
            :class="{ selected: state.status === item.key }"
            :aria-pressed="state.status === item.key"
            @click="changeQuery({ status: item.key, page: 1 })"
          >
            <span v-if="item.key === 'rejected'" class="attention-dot" />{{ item.label }}
          </button>
        </div>
        <form
          class="submission-search"
          role="search"
          @submit.prevent="changeQuery({ search: searchInput.trim(), page: 1 })"
        >
          <SearchOutlined /><input
            v-model="searchInput"
            aria-label="搜索我的投稿"
            placeholder="搜索作品名称或简介"
            maxlength="128"
          />
          <button v-if="searchInput" type="button" aria-label="清空投稿搜索" @click="clearSearch">
            <CloseOutlined />
          </button>
          <button type="submit" aria-label="搜索投稿"><ArrowRightOutlined /></button>
        </form>
      </div>
      <div class="submission-results-line">
        <span role="status"
          >{{ loading ? '正在整理你的作品…' : error ? '暂时无法加载作品' : `共 ${total} 件作品`
          }}<span v-if="state.search"> · “{{ state.search }}”</span></span
        >
        <label class="submission-sort"
          >排序<select :value="state.sort" aria-label="投稿排序" @change="changeSort">
            <option value="createTime">最近创建</option>
            <option value="editTime">最近编辑</option>
          </select></label
        >
      </div>
      <div v-if="error" class="submission-empty" role="alert">
        <CloudOutlined />
        <h2>作品暂时没有加载出来</h2>
        <p>{{ error }}</p>
        <a-button @click="fetchData">重新加载</a-button>
      </div>
      <div v-else-if="loading" class="submission-grid" aria-busy="true" aria-label="正在加载投稿">
        <div v-for="index in 6" :key="index" class="submission-skeleton">
          <div />
          <span /><span />
        </div>
      </div>
      <div v-else-if="!dataList.length" class="submission-empty">
        <div class="empty-paper"><PictureOutlined /><span /><span /></div>
        <h2>{{ isFiltered ? '这里还没有匹配的作品' : '让第一件作品，被看见。' }}</h2>
        <p>
          {{
            isFiltered
              ? '试试其他状态，或换一个关键词。'
              : '上传、完善信息，审核通过后，就能与大家分享。'
          }}
        </p>
        <a-button v-if="isFiltered" @click="resetFilters">查看全部投稿</a-button>
        <router-link v-else to="/picture/add" class="submission-create"
          >发布第一件作品 <ArrowRightOutlined
        /></router-link>
      </div>
      <div v-else class="submission-grid">
        <article
          v-for="picture in dataList"
          :key="picture.id"
          class="submission-card"
          :aria-label="picture.name || '未命名作品'"
        >
          <div class="submission-cover">
            <router-link
              :to="`/picture/${picture.id}`"
              :aria-label="`查看 ${picture.name || '未命名作品'}`"
              ><PictureThumbnail :picture="picture"
            /></router-link>
            <span :class="['submission-status', submissionState(picture).key]"
              ><i />{{ submissionState(picture).label }}</span
            >
          </div>
          <div class="submission-card-body">
            <div class="submission-card-meta">
              <span>{{ picture.category || '未分类' }}</span
              ><time>{{ displayDate(picture.createTime) }}</time>
            </div>
            <h2>
              <router-link :to="`/picture/${picture.id}`">{{
                picture.name || '等待一个好名字'
              }}</router-link>
            </h2>
            <p
              :class="[
                'submission-hint',
                { 'needs-attention': submissionState(picture).key === 'rejected' },
              ]"
            >
              {{ submissionState(picture).hint }}
            </p>
            <div class="submission-card-actions">
              <router-link
                :to="{ path: '/picture/add', query: { id: picture.id } }"
                :aria-label="`${submissionState(picture).action}：${picture.name || '未命名作品'}`"
              >
                <EditOutlined />{{ submissionState(picture).action }}<ArrowRightOutlined />
              </router-link>
              <router-link
                :to="`/picture/${picture.id}`"
                :aria-label="`作品详情：${picture.name || '未命名作品'}`"
                class="submission-detail"
                ><ArrowUpOutlined
              /></router-link>
            </div>
          </div>
        </article>
      </div>
      <div v-if="!error && total > 12" class="submission-pagination">
        <a-pagination
          :current="state.page"
          :page-size="12"
          :total="total"
          :show-size-changer="false"
          @change="(page: number) => changeQuery({ page })"
        />
      </div>
    </section>
    <p class="submission-footnote"><LockOutlined /> 私人图片留在空间，精彩作品在这里分享。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowRightOutlined,
  ArrowUpOutlined,
  CloseOutlined,
  CloudOutlined,
  EditOutlined,
  GlobalOutlined,
  LockOutlined,
  PictureOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import PictureThumbnail from '@/components/PictureThumbnail.vue'
import { listMyPictureVoByPageUsingPost } from '@/service/api/pictureController'
import {
  displayDate,
  parseSubmissionQuery,
  submissionFilters,
  submissionRequest,
  submissionState,
} from '@/utils/submissions'

const router = useRouter(),
  route = useRoute()
const state = computed(() => parseSubmissionQuery(route.query))
const searchInput = ref(state.value.search)
const dataList = ref<API.PictureVO[]>([]),
  total = ref(0),
  loading = ref(true),
  error = ref('')
const isFiltered = computed(() => state.value.status !== 'all' || !!state.value.search)
let sequence = 0
function changeQuery(patch: Partial<ReturnType<typeof parseSubmissionQuery>>) {
  const next = { ...state.value, ...patch }
  return router.replace({
    path: '/picture/mine',
    query: {
      ...(next.status !== 'all' ? { status: next.status } : {}),
      ...(next.search ? { q: next.search } : {}),
      ...(next.sort !== 'createTime' ? { order: next.sort } : {}),
      ...(next.page > 1 ? { page: String(next.page) } : {}),
    },
  })
}
function changeSort(event: Event) {
  void changeQuery({ sort: (event.target as HTMLSelectElement).value, page: 1 })
}
function clearSearch() {
  searchInput.value = ''
  void changeQuery({ search: '', page: 1 })
}
function resetFilters() {
  searchInput.value = ''
  void changeQuery({ status: 'all', search: '', page: 1 })
}
async function fetchData() {
  const current = ++sequence
  loading.value = true
  error.value = ''
  dataList.value = []
  try {
    const res = await listMyPictureVoByPageUsingPost(submissionRequest(state.value))
    if (current !== sequence) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '请稍后重试。')
    dataList.value = res.data.records || []
    total.value = Number(res.data.total || 0)
    const lastPage = Math.max(1, Math.ceil(total.value / 12))
    if (state.value.page > lastPage) void changeQuery({ page: lastPage })
  } catch (e) {
    if (current === sequence)
      error.value = e instanceof Error ? e.message : '网络连接异常，请稍后重试。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
watch(
  () => route.query,
  () => {
    if (route.name !== 'my-pictures') return
    searchInput.value = state.value.search
    void fetchData()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  sequence++
})
</script>

<style scoped>
.submissions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 0 30px;
}
.submissions-header .eyebrow {
  color: var(--muted);
  font-size: 11px;
  letter-spacing: 0.15em;
}
.submissions-header h1 {
  font-size: clamp(34px, 4vw, 46px);
  font-weight: 650;
  margin: 14px 0 12px;
  line-height: 1.2;
}
.title-period {
  color: #b7b7bf;
}
.submissions-header p {
  font-size: 14px;
  color: var(--muted);
  margin: 0;
}
.submission-create {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 14px 22px;
  border-radius: 100px;
  background: var(--ink);
  color: white;
  font-size: 13px;
  white-space: nowrap;
  transition:
    transform 0.24s,
    box-shadow 0.24s;
}
.submission-create > .anticon:last-child {
  margin-left: 12px;
}
.submission-context {
  padding: 17px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--muted);
}
.submission-context > span {
  display: flex;
  gap: 9px;
  align-items: center;
}
.submission-context a {
  color: var(--ink);
}
.submission-context a .anticon {
  transform: rotate(45deg);
  margin-left: 6px;
}
.submissions-library {
  margin-top: 32px;
}
.submission-toolbar {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
}
.submission-filters {
  display: flex;
  gap: 4px;
  overflow-x: auto;
  padding: 4px;
  background: #f5f5f7;
  border-radius: 100px;
  max-width: 100%;
}
.submission-filters button {
  flex-shrink: 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  padding: 10px 16px;
  border-radius: 100px;
  cursor: pointer;
  font-size: 12px;
  transition:
    color 0.2s,
    background 0.2s;
}
.submission-filters button.selected {
  background: white;
  color: var(--ink);
  box-shadow: 0 2px 7px #1d1d1f0b;
  font-weight: 600;
}
.attention-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #aa7841;
  margin: 0 6px 2px 0;
}
.submission-search {
  display: flex;
  align-items: center;
  gap: 9px;
  border: 1px solid var(--line);
  border-radius: 100px;
  padding: 9px 12px;
  max-width: 290px;
  width: 100%;
  color: var(--muted);
}
.submission-search:focus-within {
  border-color: #a1a1aa;
  box-shadow: 0 0 0 3px #1d1d1f05;
}
.submission-search input {
  border: 0;
  outline: 0;
  background: transparent;
  width: 100%;
  min-width: 0;
  font-size: 12px;
  color: var(--ink);
}
.submission-search button {
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 3px;
  display: grid;
  place-items: center;
  color: var(--muted);
}
.submission-results-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  font-size: 11px;
  color: var(--muted);
  margin: 24px 0 18px;
}
.submission-results-line > span {
  overflow-wrap: anywhere;
}
.submission-sort {
  flex-shrink: 0;
}
.submission-sort select {
  border: 0;
  background: transparent;
  color: var(--ink);
  margin-left: 6px;
  font-size: 11px;
  cursor: pointer;
}
.submission-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 26px;
}
.submission-card {
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
  overflow: hidden;
  transition:
    box-shadow 0.3s,
    transform 0.3s;
}
.submission-cover {
  position: relative;
  overflow: hidden;
}
.submission-cover :deep(.picture-thumbnail) {
  height: auto !important;
  aspect-ratio: 4 / 3;
}
.submission-cover :deep(img) {
  transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}
.submission-status {
  pointer-events: none;
  position: absolute;
  top: 14px;
  left: 14px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #fffffff0;
  color: #505055;
  border-radius: 100px;
  padding: 6px 10px;
  font-size: 10px;
  backdrop-filter: blur(8px);
}
.submission-status i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #929299;
}
.submission-status.published i {
  background: #53876b;
}
.submission-status.reviewing i {
  background: #658bb8;
}
.submission-status.rejected i {
  background: #b27a41;
}
.submission-card-body {
  padding: 18px 19px 10px;
}
.submission-card-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 10px;
  color: var(--muted);
}
.submission-card h2 {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.5;
  margin: 9px 0;
  overflow-wrap: anywhere;
}
.submission-hint {
  font-size: 11px;
  color: var(--muted);
  margin: 0 0 17px;
  min-height: 38px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.submission-hint.needs-attention {
  color: #916738;
}
.submission-card-actions {
  border-top: 1px solid #f0f0f2;
  padding-top: 7px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.submission-card-actions > a:first-child {
  display: flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  font-size: 11px;
}
.submission-card-actions > a:first-child .anticon:last-child {
  margin-left: 4px;
  transition: transform 0.2s;
}
.submission-detail {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--muted);
}
.submission-detail .anticon {
  transform: rotate(45deg);
}
.submission-detail:hover {
  background: var(--surface-soft);
  color: var(--ink);
}
.submission-empty {
  padding: 64px 20px 72px;
  display: flex;
  align-items: center;
  flex-direction: column;
  text-align: center;
}
.submission-empty > .anticon {
  font-size: 34px;
  color: var(--muted);
}
.submission-empty h2 {
  margin: 28px 0 8px;
  font-size: 23px;
}
.submission-empty p {
  color: var(--muted);
  font-size: 13px;
  margin: 0 0 24px;
}
.empty-paper {
  width: 78px;
  height: 96px;
  display: flex;
  flex-direction: column;
  gap: 7px;
  align-items: center;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  transform: rotate(-8deg);
  box-shadow: 7px 7px 0 #f5f5f7;
}
.empty-paper > .anticon {
  font-size: 28px;
  color: #8f8f96;
  margin-bottom: 4px;
}
.empty-paper > span {
  width: 100%;
  height: 3px;
  border-radius: 3px;
  background: #e8e8ed;
}
.empty-paper > span:last-child {
  width: 60%;
  align-self: flex-start;
}
.submission-skeleton {
  border: 1px solid var(--line);
  border-radius: 18px;
  overflow: hidden;
  padding-bottom: 30px;
}
.submission-skeleton div {
  aspect-ratio: 4 / 3;
  background: #f5f5f7;
}
.submission-skeleton span {
  display: block;
  background: #f5f5f7;
  height: 12px;
  margin: 20px 20px 0;
  width: 65%;
  border-radius: 4px;
}
.submission-skeleton span:last-child {
  width: 40%;
}
.submission-pagination {
  display: flex;
  justify-content: center;
  margin: 40px 0 8px;
}
.submission-footnote {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  font-size: 11px;
  color: var(--muted);
  margin: 34px 0 0;
}
@media (hover: hover) and (prefers-reduced-motion: no-preference) {
  .submission-create:hover {
    transform: translateY(-3px);
    box-shadow: 0 9px 20px #1d1d1f1c;
  }
  .submission-card:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 25px #1d1d1f08;
  }
  .submission-card:hover :deep(img) {
    transform: scale(1.025);
  }
  .submission-card-actions > a:first-child:hover .anticon:last-child {
    transform: translateX(3px);
  }
}
@media (min-width: 1600px) {
  .submission-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 1200px) {
  .submission-toolbar {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }
  .submission-search {
    max-width: 100%;
  }
  .submission-grid {
    gap: 18px;
  }
}
@media (max-width: 1000px) {
  .submission-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 560px) {
  .submissions-header {
    display: block;
    position: relative;
    padding: 8px 0 24px;
  }
  .submissions-header h1 {
    font-size: 32px;
  }
  .submission-create {
    padding: 12px 20px;
  }
  .submissions-header > .submission-create {
    position: absolute;
    right: 0;
    top: 38px;
    gap: 7px;
    padding: 11px 13px;
    font-size: 11px;
  }
  .submissions-header > .submission-create > .anticon:last-child {
    display: none;
  }
  .submission-context {
    font-size: 11px;
    line-height: 1.8;
    padding: 14px 0;
  }
  .submission-context > span {
    align-items: baseline;
  }
  .submission-filters {
    border-radius: 14px;
  }
  .submission-filters button {
    padding: 9px 12px;
    font-size: 11px;
  }
  .submission-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 22px;
  }
  .submissions-library {
    margin-top: 20px;
  }
  .submission-cover :deep(.picture-thumbnail) {
    aspect-ratio: 16 / 10;
  }
  .submission-footnote {
    font-size: 10px;
  }
}
</style>
