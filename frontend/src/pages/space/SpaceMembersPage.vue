<template>
  <section class="member-page">
    <PageHeader
      back="返回空间"
      eyebrow="团队空间 · 成员"
      :title="`${space?.spaceName || '团队空间'} · 成员管理`"
      description="按已注册用户的 ID 直接添加，无需邀请确认。创建者不能移除或降级，成员权限变更在后续请求生效。"
      @back="router.push(`/space/${id}`)"
    >
      <template #actions>
        <a-button :disabled="saving" aria-label="刷新成员" @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新成员
        </a-button>
      </template>
    </PageHeader>
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action><a-button @click="load">重试</a-button></template>
    </a-alert>
    <a-spin :spinning="loading">
      <template v-if="space && !error">
        <a-form layout="inline" class="add-form" :model="{ userId, role }" @finish="add">
          <span class="add-form-title"><UserAddOutlined />邀请伙伴加入</span>
          <a-form-item label="用户 ID"
            ><a-input v-model:value="userId" placeholder="粘贴完整用户 ID" :maxlength="19"
          /></a-form-item>
          <a-form-item label="角色"
            ><a-select v-model:value="role" :options="roleOptions" style="width: 120px"
          /></a-form-item>
          <a-form-item
            ><a-button html-type="submit" type="primary" :loading="saving"
              >添加成员</a-button
            ></a-form-item
          >
        </a-form>
        <div class="member-list-head">
          <span>成员</span><span>{{ members.length }} 人</span>
        </div>
        <ul class="member-list">
          <li v-for="member in members" :key="String(member.id)" class="member-row">
            <span class="member-avatar" aria-hidden="true">{{
              (member.userName || '未').slice(0, 1)
            }}</span>
            <div class="member-main">
              <div class="member-name">
                <span>{{ member.userName || '未命名用户' }}</span>
                <span v-if="member.owner" class="member-badge">创建者</span>
              </div>
              <div class="member-id">用户 ID：{{ member.userId }}</div>
            </div>
            <div class="member-actions">
              <template v-if="!member.owner">
                <a-select
                  :value="member.spaceRole"
                  :options="roleOptions"
                  :disabled="saving"
                  aria-label="修改成员角色"
                  style="width: 120px"
                  @change="(value: string) => changeRole(member, value)"
                />
                <a-button type="text" danger :disabled="saving" @click="remove(member)"
                  >移除成员</a-button
                >
              </template>
              <span v-else class="member-role">{{ roleName(member.spaceRole) }}</span>
            </div>
          </li>
        </ul>
      </template>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
import { ReloadOutlined, UserAddOutlined } from '@ant-design/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import { getSpaceVoUsingGet } from '@/service/api/spaceController'
import {
  listSpaceUserUsingPost,
  addSpaceUserUsingPost,
  editSpaceUserUsingPost,
  deleteSpaceUserUsingPost,
} from '@/service/api/spaceUserController'
import { hasPermission, permissions, parseId, sameId, roleName } from '@/utils/space'

const props = defineProps<{ id: string }>()
const router = useRouter()
const space = ref<API.SpaceVO>(),
  members = ref<API.SpaceUserVO[]>([])
const loading = ref(false),
  saving = ref(false),
  error = ref(''),
  userId = ref(''),
  role = ref('viewer')
const roleOptions = ['viewer', 'editor', 'admin'].map((value) => ({
  value,
  label: roleName(value),
}))
let sequence = 0
async function load() {
  const ticket = ++sequence
  const id = parseId(props.id)
  loading.value = true
  error.value = ''
  space.value = undefined
  members.value = []
  try {
    if (!id) throw new Error('空间 ID 无效')
    const info = await getSpaceVoUsingGet({ id })
    if (ticket !== sequence) return
    if (
      info.code !== 0 ||
      info.data?.spaceType !== 1 ||
      !hasPermission(info.data, permissions.members)
    )
      throw new Error(info.message || '没有团队成员管理权限')
    const res = await listSpaceUserUsingPost({ spaceId: id })
    if (ticket !== sequence) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '获取成员失败')
    if (res.data.some((member) => !sameId(member.spaceId, id))) throw new Error('成员归属异常')
    space.value = info.data
    members.value = res.data
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '获取成员失败'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}
async function mutate(action: () => Promise<{ code?: number; message?: string }>, target: string) {
  if (saving.value || !sameId(target, props.id)) return
  saving.value = true
  try {
    const res = await action()
    if (!sameId(target, props.id)) return
    if (res.code !== 0) throw new Error(res.message || '操作失败')
    message.success('成员已更新')
    userId.value = ''
    await load()
  } catch (e) {
    if (sameId(target, props.id)) {
      message.error(e instanceof Error ? e.message : '结果未确认，请刷新成员列表核对')
      await load()
    }
  } finally {
    saving.value = false
  }
}
function add() {
  const target = parseId(props.id),
    uid = parseId(userId.value.trim())
  if (!target || !uid) {
    message.warning('请输入有效的完整用户 ID')
    return
  }
  void mutate(
    () => addSpaceUserUsingPost({ spaceId: target, userId: uid, spaceRole: role.value }),
    target,
  )
}
function changeRole(member: API.SpaceUserVO, value: string) {
  const target = parseId(props.id),
    id = parseId(member.id)
  if (!target || !id || member.owner) return
  Modal.confirm({
    title: `将成员角色改为${roleName(value)}？`,
    content: '若修改自己的管理员角色，你将失去成员管理权限。',
    onOk: () =>
      mutate(() => editSpaceUserUsingPost({ id, spaceId: target, spaceRole: value }), target),
  })
}
function remove(member: API.SpaceUserVO) {
  const target = parseId(props.id),
    id = parseId(member.id)
  if (!target || !id || member.owner) return
  Modal.confirm({
    title: '移除此成员？',
    content: '移除后不能再读取此空间的新请求；已下载的文件无法收回。',
    onOk: () => mutate(() => deleteSpaceUserUsingPost({ id, spaceId: target }), target),
  })
}
watch(
  () => props.id,
  () => {
    userId.value = ''
    role.value = 'viewer'
    void load()
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  sequence++
  Modal.destroyAll()
})
</script>

<style scoped>
.member-page {
  width: 100%;
  max-width: 1080px;
  margin: 0 auto;
  padding: 8px 0 40px;
}
.add-form {
  align-items: center;
  gap: 12px 0;
  margin-bottom: 32px;
  padding: 18px 20px;
  border-radius: 16px;
  background: var(--surface-soft);
}
.add-form-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-right: 24px;
  font-weight: 600;
}
.member-list-head {
  display: flex;
  justify-content: space-between;
  padding: 0 4px 10px;
  color: var(--muted);
  font-size: 12px;
  letter-spacing: 0.08em;
}
.member-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border: 1px solid var(--line);
  border-radius: 18px;
  overflow: hidden;
}
.member-row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px 20px;
  background: var(--surface);
}
.member-row + .member-row {
  border-top: 1px solid var(--line);
}
.member-avatar {
  display: grid;
  flex: none;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 50%;
  background: #ececf0;
  color: var(--muted);
  font-size: 14px;
}
.member-main {
  flex: 1;
  min-width: 0;
}
.member-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 500;
}
.member-badge {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--ink);
  color: #fff;
  font-size: 11px;
  font-weight: 500;
}
.member-id {
  margin-top: 2px;
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.member-actions {
  display: flex;
  flex: none;
  align-items: center;
  gap: 8px;
}
.member-role {
  color: var(--muted);
  font-size: 13px;
}
@media (max-width: 640px) {
  .add-form {
    flex-direction: column;
    align-items: stretch;
  }
  .add-form :deep(.ant-form-item) {
    margin-inline-end: 0;
  }
  .member-row {
    flex-wrap: wrap;
  }
  .member-actions {
    width: 100%;
    padding-left: 52px;
  }
}
</style>
