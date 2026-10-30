<template>
  <section id="spaceDetailPage" :aria-busy="loading">
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" :message="error" show-icon>
        <template #action>
          <a-space wrap>
            <a-button @click="load">重试</a-button>
            <a-button v-if="mode === 'color'" @click="resetColor">返回属性搜索</a-button>
          </a-space>
        </template>
      </a-alert>
      <!-- Hide failed loads without discarding the user's search form; the gallery is cleared in load(). -->
      <div v-if="space" v-show="!error">
        <a-alert v-if="!capabilities.privateStorageConfigured" class="storage-alert" type="warning" show-icon
          :message="capabilityError || '私有图片存储尚未配置，暂不可上传或替换图片。空间和成员管理仍可使用。'" />
        <header class="space-header">
          <div class="space-heading">
            <span class="space-eyebrow"
              ><LockOutlined v-if="space.spaceType !== 1" /><TeamOutlined v-else />
              {{ space.spaceType === 1 ? '团队空间 · 与成员共同收藏' : '我的空间 · 仅自己可见' }}
            </span>
            <h2>{{ space.spaceName }}</h2>
            <p class="space-summary">
              {{ space.totalCount ?? 0 }} 张图片 <span>·</span> 已用
              {{ formatBytes(space.totalSize) }}
              <button type="button" @click="settingsOpen = true">查看用量 <RightOutlined /></button>
            </p>
          </div>
          <a-button
            v-if="can('upload')"
            type="primary"
            class="space-upload"
            :disabled="!capabilities.privateStorageConfigured"
            aria-label="上传到此空间"
            @click="upload"
            ><PlusOutlined />上传到此空间</a-button
          >
        </header>
        <nav class="space-navigation" aria-label="空间功能">
          <span class="current-section" aria-current="page"><PictureOutlined /> 图片</span>
          <div class="space-links">
            <a-button
              v-if="can('analyze')"
              type="text"
              aria-label="图库分析"
              @click="router.push(`/space/${id}/analyze`)"
              ><BarChartOutlined />图库分析</a-button
            >
            <a-button
              v-if="can('members')"
              type="text"
              aria-label="成员管理"
              @click="router.push(`/space/${id}/members`)"
              ><TeamOutlined />成员管理</a-button
            >
            <a-button
              type="text"
              :aria-label="space.canManageSpace ? '空间设置' : '空间信息'"
              @click="settingsOpen = true"
              ><SettingOutlined />{{ space.canManageSpace ? '空间设置' : '空间信息' }}</a-button
            >
          </div>
        </nav>
        <PictureSearchForm
          :key="id"
          ref="searchForm"
          compact
          :active-count="filterCount"
          :categories="categoryOptions"
          :tags="tagOptions"
          :initial-state="restoredForm"
          @search="searchPictures"
        >
          <template #tools>
            <a-button
              aria-label="颜色搜索"
              :aria-expanded="colorOpen"
              aria-controls="space-color-panel"
              :class="{ 'tool-active': colorOpen || mode === 'color' }"
              @click="colorOpen = !colorOpen"
              ><BgColorsOutlined />颜色搜索</a-button
            >
            <a-button
              v-if="can('edit') && !selectionMode"
              aria-label="选择图片"
              :disabled="loading || !pictures.length"
              @click="selectionMode = true"
              ><CheckSquareOutlined />选择图片</a-button
            >
            <a-button aria-label="刷新" title="刷新图片" :disabled="loading" @click="load"
              ><ReloadOutlined
            /></a-button>
          </template>
        </PictureSearchForm>
        <section v-if="colorOpen" id="space-color-panel" class="color-panel" aria-label="颜色搜索">
          <div>
            <h3>用颜色，找到相近的灵感。</h3>
            <p>独立于文字和属性筛选，显示颜色最接近的 12 张图片。</p>
          </div>
          <div class="color-search">
            <label for="space-color">按主色调搜索</label>
            <input id="space-color" v-model="color" type="color" aria-label="选择颜色" />
            <span>{{ color }}</span>
            <a-button :disabled="loading" @click="searchColor">搜索此颜色</a-button>
          </div>
        </section>
        <div class="results-summary" role="status">
          <span v-if="mode === 'color'"
            >颜色匹配 <span class="result-color" :style="{ background: appliedColor }" />{{
              appliedColor
            }}
            · {{ total }} 张</span
          >
          <span v-else
            >{{ hasFilters ? '搜索结果' : '全部图片' }} <strong>{{ total }}</strong></span
          >
          <a-button v-if="mode === 'color'" type="text" size="small" @click="resetColor"
            >返回属性搜索</a-button
          >
          <a-button v-else-if="hasFilters" type="text" size="small" @click="searchForm?.reset()"
            >清除条件</a-button
          >
          <span v-else class="sort-note">最近上传</span>
        </div>
        <p v-if="mode === 'color'" class="color-note">
          颜色搜索独立于上方属性条件，按相似度显示前 12 张；未提取主色调的历史图片不参与。
        </p>
        <div
          v-if="selectionMode && can('edit')"
          class="selection-bar"
          role="region"
          aria-label="批量操作"
        >
          <a-checkbox
            :checked="!!pictures.length && selectedIds.length === pictures.length"
            :disabled="loading || !pictures.length"
            :indeterminate="selectedIds.length > 0 && selectedIds.length < pictures.length"
            @update:checked="selectAll"
            >全选当前页</a-checkbox
          >
          <span>已选 {{ selectedIds.length }} 张</span>
          <div class="selection-actions">
            <a-button type="primary" :disabled="!selectedIds.length || loading" @click="openBatch"
              >批量编辑（{{ selectedIds.length }}）</a-button
            >
            <a-button @click="finishSelection">完成选择</a-button>
          </div>
        </div>
        <div v-if="!pictures.length && !loading" class="space-empty">
          <a-empty
            :description="
              hasFilters || mode === 'color' ? '没有找到匹配的图片' : '这里，留给你的下一份灵感。'
            "
          />
          <a-button
            v-if="!hasFilters && mode !== 'color' && can('upload')"
            type="primary"
            :disabled="!capabilities.privateStorageConfigured"
            @click="upload"
            >上传第一张图片</a-button
          >
          <a-button v-else-if="mode === 'attributes' && hasFilters" @click="searchForm?.reset()"
            >清除筛选，查看全部</a-button
          >
        </div>
        <div class="gallery">
          <article
            v-for="item in pictures"
            :key="String(item.id)"
            class="space-picture-card"
            :class="{ selected: selectionMode && selectedIds.includes(String(item.id)) }"
          >
            <div class="space-picture-cover">
              <PictureThumbnail :picture="item" :height="220" :preview="!selectionMode" />
              <button
                v-if="selectionMode && can('edit')"
                class="select-cover"
                :aria-label="`选择 ${item.name || '未命名图片'}`"
                :aria-pressed="selectedIds.includes(String(item.id))"
                @click="selectPicture(item, !selectedIds.includes(String(item.id)))"
              />
              <span v-if="item.uploadStatus === 0" class="draft-label">草稿 · 待完善信息</span>
            </div>
            <div class="space-picture-caption">
              <router-link :to="`/picture/${item.id}`" class="picture-name">{{
                item.name || '未命名图片'
              }}</router-link>
              <a-dropdown v-if="!selectionMode" :trigger="['click']">
                <a-button
                  type="text"
                  class="picture-menu"
                  :aria-label="`图片操作 ${item.name || '未命名图片'}`"
                  ><EllipsisOutlined
                /></a-button>
                <template #overlay
                  ><a-menu>
                    <a-menu-item key="view" @click="router.push(`/picture/${item.id}`)"
                      ><EyeOutlined /> 查看详情</a-menu-item
                    >
                    <a-menu-item v-if="can('edit')" key="edit" :disabled="!capabilities.privateStorageConfigured" @click="upload(item)"
                      ><EditOutlined /> 编辑 / 替换</a-menu-item
                    >
                    <a-menu-item key="share" @click="share(item)"
                      ><ExportOutlined /> 分享</a-menu-item
                    >
                    <a-menu-divider v-if="can('delete')" />
                    <a-menu-item v-if="can('delete')" key="delete" danger @click="remove(item)"
                      ><DeleteOutlined /> 删除</a-menu-item
                    >
                  </a-menu></template
                >
              </a-dropdown>
            </div>
            <div class="space-picture-meta">
              <a-checkbox
                v-if="selectionMode && can('edit')"
                :checked="selectedIds.includes(String(item.id))"
                @update:checked="(checked: boolean) => selectPicture(item, checked)"
                >选择 {{ item.name || '未命名图片' }}</a-checkbox
              >
              <template v-else
                ><span>{{ item.category || '未分类' }}</span
                ><span>{{ item.picFormat?.toUpperCase() || 'IMAGE' }}</span></template
              >
            </div>
          </article>
        </div>
        <a-pagination
          v-if="total > 12 && mode === 'attributes'"
          :current="current"
          :page-size="12"
          :total="total"
          :show-size-changer="false"
          @change="changePage"
        />
      </div>
    </a-spin>
    <a-drawer
      v-if="space && !error"
      v-model:open="settingsOpen"
      :title="space.canManageSpace ? '空间设置' : '空间信息'"
      width="min(420px, 100vw)"
    >
      <section class="space-settings" :aria-label="space.canManageSpace ? '空间设置' : '空间信息'">
        <h3>{{ space.spaceName }}</h3>
        <a-tag>{{ spaceLevelName(space.spaceLevel) }}</a-tag>
        <p>
          {{
            space.spaceType === 1
              ? '团队成员按角色访问，草稿也对有查看权限的成员可见。'
              : '仅你本人可访问。'
          }}空间图片不进入公共图库和公开审核。
        </p>
        <h4>存储用量</h4>
        <SpaceUsage :space="space" />
        <div class="settings-actions">
          <a-button v-if="space.canManageSpace" block @click="openRename"
            ><EditOutlined />重命名空间</a-button
          >
          <a-button
            block
            @click="router.push(space.spaceType === 1 ? '/space/teams' : '/space/mine?view=list')"
            ><FolderOpenOutlined />{{
              space.spaceType === 1 ? '返回团队空间' : '查看空间列表'
            }}</a-button
          >
        </div>
        <section v-if="space.canManageSpace" class="danger-zone">
          <h4>删除空间</h4>
          <p>此操作需再次确认，仅没有图片引用的空间可以删除。</p>
          <a-button danger @click="deleteSpace">删除空空间</a-button>
        </section>
      </section>
    </a-drawer>
    <PictureShareModal v-model:open="shareOpen" :picture="sharePicture" />
    <BatchEditPictureModal
      v-if="batchOpen"
      v-model:open="batchOpen"
      :space-id="id"
      :pictures="batchPictures"
      @success="load"
    />
    <a-modal v-model:open="renameOpen" title="重命名空间" :confirm-loading="saving" @ok="rename">
      <a-input v-model:value="newName" :maxlength="30" placeholder="空间名称" />
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  LockOutlined,
  TeamOutlined,
  RightOutlined,
  PlusOutlined,
  PictureOutlined,
  BarChartOutlined,
  SettingOutlined,
  BgColorsOutlined,
  CheckSquareOutlined,
  ReloadOutlined,
  EllipsisOutlined,
  EyeOutlined,
  EditOutlined,
  ExportOutlined,
  DeleteOutlined,
  FolderOpenOutlined,
} from '@ant-design/icons-vue'
import { useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useNavigationStore } from '@/stores/useNavigationStore'
import { message, Modal } from 'ant-design-vue'
import {
  getSpaceVoUsingGet,
  editSpaceUsingPost,
  deleteSpaceUsingPost,
} from '@/service/api/spaceController'
import {
  listPictureVoByPageUsingPost,
  deletePictureUsingPost,
  listPictureTagCategoryUsingGet,
} from '@/service/api/pictureController'
import {
  parseId,
  sameId,
  spaceLevelName,
  permissions,
  hasPermission,
  formatBytes,
} from '@/utils/space'
import SpaceUsage from '@/components/SpaceUsage.vue'
import PictureThumbnail from '@/components/PictureThumbnail.vue'
import PictureSearchForm from '@/components/PictureSearchForm.vue'
import PictureShareModal from '@/components/PictureShareModal.vue'
import BatchEditPictureModal from '@/components/BatchEditPictureModal.vue'
import { searchPictureByColorUsingPost } from '@/service/api/pictureExtensionController'
import type { PictureSearchFilters, PictureSearchValues } from '@/utils/pictureSearch'
import { usePictureCapabilities } from '@/composables/usePictureCapabilities'
const { capabilities, capabilityError } = usePictureCapabilities()

const props = defineProps<{ id: string }>()
const router = useRouter()
const navigation = useNavigationStore()
type SearchFormState = {
  values: PictureSearchValues
  dateRange?: [string, string]
  expanded: string[]
  filtersOpen: boolean
}
type SpaceView = {
  search: PictureSearchFilters
  current: number
  mode: 'attributes' | 'color'
  color: string
  appliedColor: string
  colorOpen: boolean
  form?: SearchFormState
}
const restoredForm = ref<SearchFormState>()
const space = ref<API.SpaceVO>()
const can = (action: keyof typeof permissions) => hasPermission(space.value, permissions[action])
const pictures = ref<API.PictureVO[]>([])
const loading = ref(false)
const error = ref('')
const search = ref<PictureSearchFilters>({})
const searchForm = ref<InstanceType<typeof PictureSearchForm>>()
const hasFilters = computed(() => Object.keys(search.value).length > 0)
const filterCount = computed(
  () => Object.keys(search.value).filter((key) => key !== 'searchText').length,
)
const settingsOpen = ref(false)
const colorOpen = ref(false)
const selectionMode = ref(false)
const categoryOptions = ref<string[]>([])
const tagOptions = ref<string[]>([])
const current = ref(1)
const total = ref(0)
const renameOpen = ref(false)
const saving = ref(false)
const newName = ref('')
const mode = ref<'attributes' | 'color'>('attributes')
const color = ref('#ff0000')
const appliedColor = ref('#ff0000')
const selectedIds = ref<string[]>([])
const batchOpen = ref(false)
const batchPictures = ref<API.PictureVO[]>([])
const shareOpen = ref(false)
const sharePicture = ref<API.PictureVO>()
let sequence = 0
function rememberView() {
  navigation.rememberList(`space:${props.id}`, {
    search: search.value,
    current: current.value,
    mode: mode.value,
    color: color.value,
    appliedColor: appliedColor.value,
    colorOpen: colorOpen.value,
    form: searchForm.value?.snapshot(),
  })
}
onBeforeRouteLeave(rememberView)
onBeforeRouteUpdate((to, from) => {
  if (to.params.id !== from.params.id) rememberView()
})
function finishSelection() {
  selectionMode.value = false
  selectedIds.value = []
}
function share(item: API.PictureVO) {
  sharePicture.value = item
  shareOpen.value = true
}
function selectAll(checked: boolean) {
  selectedIds.value = checked ? pictures.value.map((item) => String(item.id)) : []
}
function selectPicture(item: API.PictureVO, checked: boolean) {
  const id = parseId(item.id)
  if (!id) return
  selectedIds.value = checked
    ? [...new Set([...selectedIds.value, id])]
    : selectedIds.value.filter((value) => value !== id)
}
function openBatch() {
  batchPictures.value = pictures.value
    .filter((item) => selectedIds.value.includes(String(item.id)))
    .map((item) => ({ ...item }))
  batchOpen.value = batchPictures.value.length > 0
}
function searchColor() {
  appliedColor.value = color.value
  mode.value = 'color'
  current.value = 1
  load()
}
function resetColor() {
  mode.value = 'attributes'
  current.value = 1
  load()
}
function openRename() {
  newName.value = space.value?.spaceName || ''
  renameOpen.value = true
}
function searchPictures(filters: PictureSearchFilters) {
  mode.value = 'attributes'
  search.value = filters
  current.value = 1
  load()
}
function changePage(page: number) {
  current.value = page
  load()
}

async function load() {
  const ticket = ++sequence
  const id = parseId(props.id)
  loading.value = true
  selectedIds.value = []
  batchOpen.value = false
  shareOpen.value = false
  error.value = ''
  // Keep the form mounted on a same-space refresh so entered/applied filters survive pagination.
  if (!sameId(space.value?.id, id)) space.value = undefined
  pictures.value = []
  total.value = 0
  try {
    if (!id) throw new Error('空间 ID 无效')
    const info = await getSpaceVoUsingGet({ id })
    if (ticket !== sequence) return
    if (info.code !== 0 || !info.data) throw new Error(info.message || '无法获取空间')
    // 管理员能看空间元数据，不代表能查看他人的私人图片。
    if (!hasPermission(info.data, permissions.view)) throw new Error('没有此空间的查看权限')
    navigation.rememberSpace(info.data)
    if (mode.value === 'color') {
      const res = await searchPictureByColorUsingPost({ spaceId: id, picColor: appliedColor.value })
      if (ticket !== sequence) return
      if (res.code !== 0 || !Array.isArray(res.data)) throw new Error(res.message || '颜色搜索失败')
      if (res.data.some((item) => !sameId(item.spaceId, id)))
        throw new Error('图片归属异常，请刷新重试')
      space.value = info.data
      pictures.value = res.data
      total.value = res.data.length
      return
    }
    const page = await listPictureVoByPageUsingPost({
      ...search.value,
      spaceId: id,
      current: current.value,
      pageSize: 12,
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (ticket !== sequence) return
    if (page.code !== 0 || !page.data) throw new Error(page.message || '获取空间图片失败')
    if (page.data.records?.some((item) => !sameId(item.spaceId, id)))
      throw new Error('图片归属异常，请刷新重试')
    space.value = info.data
    pictures.value = page.data.records || []
    total.value = Number(page.data.total || 0)
    const lastPage = Math.max(1, Math.ceil(total.value / 12))
    if (current.value > lastPage) {
      current.value = lastPage
      await load()
    }
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '获取空间失败'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}

function upload(picture?: API.PictureVO | Event) {
  if (!capabilities.value.privateStorageConfigured) { message.warning('私有图片存储尚未配置'); return }
  const id = parseId(space.value?.id)
  if (!id) return
  const pictureId = picture && 'id' in picture ? parseId(picture.id) : undefined
  router.push({
    path: '/picture/add',
    query: { spaceId: id, ...(pictureId ? { id: pictureId } : {}) },
  })
}

async function rename() {
  const id = parseId(space.value?.id)
  if (!id || saving.value) return
  if (!newName.value.trim()) {
    message.warning('请输入空间名称')
    return
  }
  saving.value = true
  try {
    const res = await editSpaceUsingPost({ id, spaceName: newName.value.trim() })
    if (res.code !== 0) throw new Error(res.message || '修改失败')
    renameOpen.value = false
    message.success('空间名称已更新')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '修改失败')
  } finally {
    saving.value = false
  }
}

function remove(item: API.PictureVO) {
  const id = parseId(item.id)
  if (!id) return
  Modal.confirm({
    title: '删除这张私人图片？',
    content: '删除后释放图片数量和容量，云端文件由后台清理。',
    async onOk() {
      try {
        const res = await deletePictureUsingPost({ id })
        if (res.code !== 0) throw new Error(res.message || '删除失败')
        message.success('图片已删除')
        if (pictures.value.length === 1 && current.value > 1) current.value--
        await load()
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    },
  })
}

function deleteSpace() {
  const id = parseId(space.value?.id)
  if (!id) return
  Modal.confirm({
    title: '删除空空间？',
    content: '仅没有图片引用的空间可以删除。已逻辑删除的历史图片仍可能阻止删除空间。',
    async onOk() {
      try {
        const res = await deleteSpaceUsingPost({ id })
        if (res.code !== 0) throw new Error(res.message || '删除失败')
        message.success('空间已删除')
        await router.push(space.value?.spaceType === 1 ? '/space/teams' : '/space/mine')
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除失败')
      }
    },
  })
}
watch(
  () => props.id,
  () => {
    const restored = navigation.readList<SpaceView>(`space:${props.id}`)
    current.value = restored?.current || 1
    search.value = restored?.search || {}
    mode.value = restored?.mode || 'attributes'
    color.value = restored?.color || '#ff0000'
    appliedColor.value = restored?.appliedColor || '#ff0000'
    restoredForm.value = restored?.form
    renameOpen.value = false
    settingsOpen.value = false
    colorOpen.value = restored?.colorOpen || false
    finishSelection()
    load()
  },
  { immediate: true },
)
onMounted(async () => {
  try {
    const res = await listPictureTagCategoryUsingGet()
    if (res.code === 0) {
      categoryOptions.value = res.data?.categoryList || []
      tagOptions.value = res.data?.tagList || []
    }
  } catch {
    // Suggestions are optional: typed category/tag filters remain usable.
  }
})
onBeforeUnmount(() => {
  sequence++
})
</script>

<style scoped>
#spaceDetailPage {
  padding: 8px 0 40px;
  max-width: 1440px;
  margin: 0 auto;
}
.storage-alert {
  margin: 8px 0 28px;
}
.space-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 28px;
}
.space-heading {
  min-width: 0;
}
.space-eyebrow {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--muted);
  font-size: 11px;
}
.space-heading h2 {
  font-size: 32px;
  font-weight: 650;
  margin: 11px 0 10px;
  overflow-wrap: anywhere;
}
.space-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 12px;
  color: var(--muted);
  margin: 0;
}
.space-summary button {
  border: 0;
  background: none;
  padding: 0 4px;
  font-size: 11px;
  cursor: pointer;
  color: var(--ink);
}
.space-summary button .anticon {
  font-size: 8px;
}
.space-upload {
  border-radius: 100px;
  padding-inline: 20px;
  flex-shrink: 0;
}
.space-navigation,
.space-links {
  display: flex;
  align-items: center;
  gap: 8px;
}
.space-navigation {
  justify-content: space-between;
  border-bottom: 1px solid var(--line);
  min-height: 48px;
}
.current-section {
  display: flex;
  align-items: center;
  gap: 8px;
  align-self: stretch;
  border-bottom: 2px solid var(--ink);
  padding: 0 4px;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}
.space-links {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0;
}
.space-links :deep(.ant-btn) {
  color: var(--muted);
  font-size: 12px;
  padding-inline: 10px;
}
.space-links :deep(.ant-btn:hover) {
  color: var(--ink);
}
.tool-active {
  background: var(--surface-soft);
  border-color: #b8b8c0;
}
.results-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 56px;
  color: var(--muted);
  font-size: 12px;
}
.results-summary > span:first-child {
  display: flex;
  gap: 8px;
  align-items: center;
}
.results-summary strong {
  font-size: 11px;
  font-weight: 500;
}
.sort-note {
  font-size: 11px;
}
.result-color {
  width: 12px;
  height: 12px;
  border: 1px solid #00000014;
  border-radius: 50%;
}
.color-panel {
  padding: 20px 24px;
  margin-top: 16px;
  border-radius: 18px;
  background: var(--surface);
  border: 1px solid var(--line);
}
.color-panel h3 {
  font-size: 15px;
  margin: 0 0 6px;
}
.color-panel p,
.color-note {
  font-size: 12px;
  color: var(--muted);
  margin-bottom: 12px;
}
.color-search,
.selection-bar,
.selection-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.color-search {
  font-size: 12px;
  gap: 12px;
}
.color-search input {
  width: 34px;
  height: 34px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 8px;
  cursor: pointer;
  background: white;
}
.selection-bar {
  padding: 12px 16px;
  margin-bottom: 20px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--surface-soft);
  font-size: 12px;
}
.selection-actions {
  margin-left: auto;
}
.space-empty {
  padding: 56px 0;
  text-align: center;
  background: var(--surface);
  border: 1px dashed var(--line);
  border-radius: 20px;
}
.space-empty :deep(.ant-empty) {
  padding: 0;
  margin-bottom: 20px;
}
.gallery {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px 22px;
  margin: 0 0 30px;
}
.space-picture-card {
  min-width: 0;
}
.space-picture-cover {
  position: relative;
  border-radius: 16px;
  overflow: hidden;
  background: var(--surface-soft);
}
.space-picture-cover :deep(img) {
  transition: transform 350ms var(--motion-ease);
}
.space-picture-caption {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}
.picture-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.picture-menu {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--muted);
  /* 点击区域放大到 36px，便于在手机上点中 */
  width: 36px;
  height: 36px;
  margin-right: -8px;
  padding: 0;
}
.space-picture-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 11px;
  color: var(--muted);
  margin-top: 2px;
  min-height: 22px;
}
.space-picture-meta :deep(.ant-checkbox-wrapper) {
  font-size: 12px;
  overflow-wrap: anywhere;
}
.draft-label {
  position: absolute;
  left: 12px;
  top: 12px;
  background: #ffffffed;
  border-radius: 100px;
  padding: 5px 10px;
  font-size: 10px;
  pointer-events: none;
}
.select-cover {
  position: absolute;
  inset: 0;
  border: 0;
  width: 100%;
  background: transparent;
  cursor: pointer;
}
.selected .space-picture-cover {
  outline: 2px solid var(--ink);
  outline-offset: 3px;
}
.space-settings h3 {
  font-size: 22px;
  overflow-wrap: anywhere;
  margin-bottom: 12px;
}
.space-settings h4 {
  margin-top: 28px;
}
.space-settings p {
  font-size: 12px;
  color: var(--muted);
}
.settings-actions {
  display: grid;
  gap: 12px;
  margin: 28px 0;
}
.danger-zone {
  border-top: 1px solid var(--line);
  padding-top: 8px;
}
.danger-zone h4 {
  font-size: 13px;
}
@media (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .space-picture-cover:hover :deep(img) {
    transform: scale(1.025);
  }
}
@media (min-width: 1600px) {
  .gallery {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}
@media (max-width: 1100px) {
  .gallery {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .space-header {
    flex-wrap: wrap;
    gap: 18px;
    margin-bottom: 20px;
  }
  .space-heading {
    width: 100%;
  }
  .space-heading h2 {
    font-size: 28px;
  }
  .space-upload {
    height: 38px;
  }
  .space-links :deep(.ant-btn) {
    font-size: 11px;
    padding-inline: 7px;
  }
  .gallery {
    gap: 22px 14px;
  }
  .space-picture-cover {
    border-radius: 12px;
  }
  .space-picture-cover :deep(.picture-thumbnail) {
    height: 170px !important;
  }
  .picture-name {
    font-size: 12px;
  }
  .draft-label {
    left: 6px;
    top: 6px;
    font-size: 9px;
    padding: 4px 6px;
  }
  .selection-actions {
    width: 100%;
    margin: 4px 0 0;
  }
  .color-panel {
    padding: 18px;
  }
}
@media (max-width: 360px) {
  .gallery {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
