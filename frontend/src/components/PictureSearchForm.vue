<template>
  <a-form
    name="picture-search"
    class="picture-search-form"
    :class="{ compact }"
    layout="vertical"
    :model="values"
    @finish="search"
  >
    <div v-if="compact" class="quick-search">
      <div class="keyword-search">
        <a-input
          v-model:value="values.searchText"
          aria-label="关键词"
          placeholder="搜索空间内图片"
          :maxlength="128"
          allow-clear
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <a-button html-type="submit" aria-label="搜索">搜索</a-button>
      </div>
      <div class="quick-tools">
        <a-button
          :aria-expanded="filtersOpen"
          aria-controls="space-filters"
          aria-label="筛选"
          :class="{ 'tool-active': filtersOpen || activeCount }"
          @click="filtersOpen = !filtersOpen"
        >
          <FilterOutlined /> 筛选
          <span v-if="activeCount" class="filter-count" aria-hidden="true">{{ activeCount }}</span>
        </a-button>
        <slot name="tools" />
      </div>
    </div>
    <div id="space-filters" v-show="!compact || filtersOpen" class="filter-panel">
      <div class="filter-grid">
        <a-form-item v-if="!compact" label="关键词" name="searchText">
          <a-input
            v-model:value="values.searchText"
            placeholder="搜索空间内图片"
            :maxlength="128"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="分类" name="category">
          <a-auto-complete
            v-model:value="values.category"
            :options="categoryOptions"
            placeholder="选择或输入分类"
            :maxlength="64"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="标签（同时包含）" name="tags">
          <a-select
            v-model:value="values.tags"
            mode="tags"
            :options="tagOptions"
            placeholder="选择或输入标签"
            :max-tag-count="2"
            allow-clear
          />
        </a-form-item>
      </div>
      <a-collapse v-model:active-key="expanded" :bordered="false">
        <a-collapse-panel key="details" header="详细筛选">
          <div class="filter-grid">
            <a-form-item label="编辑日期（北京时间，包含结束日）" class="date-filter">
              <a-range-picker
                v-model:value="dateRange"
                value-format="YYYY-MM-DD"
                :placeholder="['开始日期', '结束日期']"
              />
              <a-space class="date-presets" wrap>
                <a-button size="small" @click="dateRange = recentEditDates(7)">近 7 天</a-button>
                <a-button size="small" @click="dateRange = recentEditDates(30)">近 30 天</a-button>
              </a-space>
            </a-form-item>
            <a-form-item label="图片名称" name="name">
              <a-input
                v-model:value="values.name"
                placeholder="按名称筛选"
                :maxlength="128"
                allow-clear
              />
            </a-form-item>
            <a-form-item label="图片简介" name="introduction">
              <a-input
                v-model:value="values.introduction"
                placeholder="按简介筛选"
                :maxlength="500"
                allow-clear
              />
            </a-form-item>
            <a-form-item label="宽度（像素，精确匹配）" name="picWidth">
              <a-input-number
                v-model:value="values.picWidth"
                placeholder="例如 1920"
                :min="1"
                :max="2147483647"
                :precision="0"
              />
            </a-form-item>
            <a-form-item label="高度（像素，精确匹配）" name="picHeight">
              <a-input-number
                v-model:value="values.picHeight"
                placeholder="例如 1080"
                :min="1"
                :max="2147483647"
                :precision="0"
              />
            </a-form-item>
            <a-form-item label="图片格式" name="picFormat">
              <a-select
                v-model:value="values.picFormat"
                :options="formatOptions"
                placeholder="全部格式"
                allow-clear
              />
            </a-form-item>
          </div>
        </a-collapse-panel>
      </a-collapse>
      <a-space class="search-actions">
        <a-button type="primary" html-type="submit">{{ compact ? '应用筛选' : '搜索' }}</a-button>
        <a-button @click="reset">重置</a-button>
      </a-space>
    </div>
  </a-form>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { SearchOutlined, FilterOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  buildPictureSearchFilters,
  recentEditDates,
  type PictureSearchFilters,
  type PictureSearchValues,
} from '@/utils/pictureSearch'

const props = defineProps<{
  categories?: string[]
  tags?: string[]
  compact?: boolean
  activeCount?: number
  initialState?: {
    values: PictureSearchValues
    dateRange?: [string, string]
    expanded: string[]
    filtersOpen: boolean
  }
}>()
const emit = defineEmits<{ search: [filters: PictureSearchFilters] }>()
const values = ref<PictureSearchValues>(props.initialState?.values || {})
const dateRange = ref<[string, string] | undefined>(props.initialState?.dateRange)
const expanded = ref<string[]>(props.initialState?.expanded || [])
const filtersOpen = ref(props.initialState?.filtersOpen || false)
const categoryOptions = computed(() => (props.categories || []).map((value) => ({ value })))
const tagOptions = computed(() => (props.tags || []).map((value) => ({ value })))
const formatOptions = ['jpeg', 'jpg', 'png', 'webp', 'gif'].map((value) => ({
  value,
  label: value.toUpperCase(),
}))

function search() {
  try {
    emit('search', buildPictureSearchFilters(values.value, dateRange.value))
  } catch (error) {
    message.warning(error instanceof Error ? error.message : '请检查搜索条件')
  }
}
function reset() {
  values.value = {}
  dateRange.value = undefined
  emit('search', {})
}
function snapshot() {
  return {
    values: values.value,
    dateRange: dateRange.value,
    expanded: expanded.value,
    filtersOpen: filtersOpen.value,
  }
}
defineExpose({ reset, snapshot })
</script>

<style scoped>
.picture-search-form {
  margin: 20px 0;
}
.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 16px;
}
.filter-grid :deep(.ant-form-item),
.filter-grid :deep(.ant-form-item-control-input-content) {
  min-width: 0;
}
.filter-grid :deep(.ant-picker),
.filter-grid :deep(.ant-input-number) {
  width: 100%;
}
.date-filter {
  grid-column: 1 / -1;
  max-width: 560px;
}
.date-presets {
  margin-top: 8px;
}
.search-actions {
  margin-top: 12px;
}
.compact {
  margin: 24px 0 0;
}
.quick-search,
.keyword-search,
.quick-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.quick-search {
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.keyword-search {
  flex: 1 1 240px;
  max-width: 440px;
}
.keyword-search :deep(.ant-input-affix-wrapper) {
  border-radius: 12px;
  background: var(--surface);
}
.keyword-search :deep(.ant-input-prefix) {
  color: var(--muted);
  margin-right: 10px;
}
.quick-tools {
  flex-wrap: wrap;
}
.quick-tools :deep(.ant-btn) {
  font-size: 12px;
  padding-inline: 12px;
}
.tool-active {
  background: var(--surface-soft);
  border-color: #b8b8c0;
}
.filter-count {
  font-size: 11px;
}
.compact .filter-panel {
  margin-top: 16px;
  padding: 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
}
.compact .filter-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
@media (max-width: 720px) {
  .filter-grid,
  .compact .filter-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .date-filter {
    max-width: 100%;
  }
  .keyword-search {
    flex-basis: 100%;
    max-width: none;
  }
  .compact .filter-panel {
    padding: 16px;
  }
}
</style>
