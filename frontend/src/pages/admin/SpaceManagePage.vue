<template>
  <section class="manage-page">
    <h2>空间管理</h2>
    <a-alert v-if="user.loginUser.userRole !== 1" type="error" message="仅平台管理员可访问" />
    <template v-else>
      <p>这里只管理空间元数据和额度，不授予查看私人或团队图片的权限。空间类型创建后不可更改。</p>
      <a-space wrap>
        <a-input v-model:value="name" placeholder="空间名称" :maxlength="30" />
        <a-select
          v-model:value="type"
          :options="typeOptions"
          style="width: 140px"
          aria-label="空间类型"
        />
        <a-button type="primary" :loading="loading" @click="search">查询</a-button>
      </a-space>
      <a-alert v-if="error" :message="error" type="error" show-icon />
      <a-spin :spinning="loading">
        <div class="spaces">
          <a-card v-for="space in records" :key="String(space.id)" :title="space.spaceName">
            <a-tag>{{ spaceTypeName(space.spaceType) }}</a-tag
            ><a-tag>{{ spaceLevelName(space.spaceLevel) }}</a-tag>
            <p>空间 ID：{{ space.id }} · 创建者：{{ space.userId }}</p>
            <SpaceUsage :space="space" />
            <a-space wrap
              ><a-button @click="edit(space)">调整名称与额度</a-button
              ><a-button danger @click="remove(space)">删除空空间</a-button></a-space
            >
          </a-card>
        </div>
        <a-empty v-if="!records.length && !loading && !error" />
        <a-pagination
          v-if="total"
          :current="current"
          :page-size="12"
          :total="total"
          :show-size-changer="false"
          @change="changePage"
        />
      </a-spin>
      <a-modal v-model:open="editOpen" title="调整空间" :confirm-loading="saving" @ok="save">
        <a-form layout="vertical">
          <a-form-item label="名称"
            ><a-input v-model:value="draft.spaceName" :maxlength="30"
          /></a-form-item>
          <a-form-item label="等级"
            ><a-select v-model:value="draft.spaceLevel" :options="levelOptions"
          /></a-form-item>
          <a-form-item label="容量上限（字节）"
            ><a-input v-model:value="size" inputmode="numeric"
          /></a-form-item>
          <a-form-item label="图片数上限"
            ><a-input v-model:value="count" inputmode="numeric"
          /></a-form-item>
        </a-form>
        <p>本表单使用显式额度，不会因调整等级自动覆盖已填额度；额度不可低于已使用量。</p>
      </a-modal>
    </template>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { message, Modal } from 'ant-design-vue'
import {
  listSpacesUsingPost,
  updateSpaceUsingPost,
  deleteSpaceUsingPost,
} from '@/service/api/spaceController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { parseId, spaceTypeName, spaceLevelName } from '@/utils/space'
import SpaceUsage from '@/components/SpaceUsage.vue'
const user = useLoginUserStore()
const name = ref(''),
  type = ref(-1),
  current = ref(1),
  total = ref(0)
const records = ref<API.Space[]>([]),
  error = ref(''),
  loading = ref(false),
  saving = ref(false),
  editOpen = ref(false)
const draft = ref<API.SpaceUpdateRequest>({}),
  size = ref(''),
  count = ref('')
const typeOptions = [
  { value: -1, label: '全部类型' },
  { value: 0, label: '私有空间' },
  { value: 1, label: '团队空间' },
]
const levelOptions = [0, 1, 2].map((value) => ({ value, label: spaceLevelName(value) }))
let sequence = 0
async function load() {
  if (user.loginUser.userRole !== 1) return
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  records.value = []
  try {
    const res = await listSpacesUsingPost({
      current: current.value,
      pageSize: 12,
      spaceName: name.value.trim(),
      spaceType: type.value < 0 ? undefined : type.value,
    })
    if (ticket !== sequence) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '查询失败')
    records.value = res.data.records || []
    total.value = Number(res.data.total || 0)
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '查询失败'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}
function search() {
  current.value = 1
  void load()
}
function changePage(page: number) {
  current.value = page
  void load()
}
function edit(space: API.Space) {
  draft.value = { id: space.id, spaceName: space.spaceName, spaceLevel: space.spaceLevel }
  size.value = String(space.maxSize || '')
  count.value = String(space.maxCount || '')
  editOpen.value = true
}
async function save() {
  if (saving.value) return
  const maxSize = parseId(size.value),
    maxCount = parseId(count.value)
  if (!maxSize || !maxCount || !draft.value.spaceName?.trim()) {
    message.warning('名称和额度必须有效')
    return
  }
  saving.value = true
  try {
    const res = await updateSpaceUsingPost({ ...draft.value, maxSize, maxCount })
    if (res.code !== 0) throw new Error(res.message || '修改失败')
    editOpen.value = false
    message.success('已更新')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '结果未确认，请刷新核对')
  } finally {
    saving.value = false
  }
}
function remove(space: API.Space) {
  const id = parseId(space.id)
  if (!id) return
  Modal.confirm({
    title: '删除空空间？',
    content: '含历史图片引用的空间不可删除。团队成员关系会一并移除。',
    async onOk() {
      const res = await deleteSpaceUsingPost({ id })
      if (res.code !== 0) {
        message.error(res.message || '删除失败')
        return
      }
      message.success('已删除')
      await load()
    },
  })
}
onMounted(load)
onBeforeUnmount(() => {
  sequence++
})
</script>

<style scoped>
.manage-page {
  padding: 24px;
  max-width: 1100px;
  margin: auto;
}
.spaces {
  display: grid;
  gap: 16px;
  margin: 20px 0;
}
p {
  overflow-wrap: anywhere;
}
</style>
