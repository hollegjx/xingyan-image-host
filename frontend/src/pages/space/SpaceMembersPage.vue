<template>
  <section class="member-page">
    <a-button type="link" @click="router.push(`/space/${id}`)">返回空间</a-button>
    <h2>{{ space?.spaceName || '团队空间' }} · 成员管理</h2>
    <a-alert v-if="error" :message="error" type="error" show-icon>
      <template #action><a-button @click="load">重试</a-button></template>
    </a-alert>
    <a-spin :spinning="loading">
      <template v-if="space && !error">
        <p>
          按已注册用户的 ID
          直接添加，无需邀请确认。创建者不能移除或降级，成员权限变更在后续请求生效。
        </p>
        <a-form layout="inline" class="add-form" :model="{ userId, role }" @finish="add">
          <a-form-item label="用户 ID"
            ><a-input v-model:value="userId" placeholder="粘贴完整用户 ID" :maxlength="19"
          /></a-form-item>
          <a-form-item label="角色"
            ><a-select v-model:value="role" :options="roleOptions" style="width: 140px"
          /></a-form-item>
          <a-form-item
            ><a-button html-type="submit" type="primary" :loading="saving"
              >添加成员</a-button
            ></a-form-item
          >
        </a-form>
        <a-button :disabled="saving" @click="load">刷新成员</a-button>
        <div class="member-list">
          <a-card
            v-for="member in members"
            :key="String(member.id)"
            :title="member.userName || '未命名用户'"
          >
            <p>用户 ID：{{ member.userId }}</p>
            <a-tag>{{ roleName(member.spaceRole) }}</a-tag
            ><a-tag v-if="member.owner">创建者</a-tag>
            <a-space v-if="!member.owner" wrap>
              <a-select
                :value="member.spaceRole"
                :options="roleOptions"
                :disabled="saving"
                aria-label="修改成员角色"
                style="width: 140px"
                @change="(value: string) => changeRole(member, value)"
              />
              <a-button danger :disabled="saving" @click="remove(member)">移除成员</a-button>
            </a-space>
          </a-card>
        </div>
      </template>
    </a-spin>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { message, Modal } from 'ant-design-vue'
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
  max-width: 1000px;
  margin: auto;
  padding: 24px;
}
.add-form {
  gap: 12px;
  margin: 20px 0;
}
.member-list {
  display: grid;
  gap: 16px;
  margin-top: 20px;
}
p {
  overflow-wrap: anywhere;
}
@media (max-width: 640px) {
  .add-form { flex-direction: column; }
  .add-form :deep(.ant-form-item) { margin-inline-end: 0; }
}
</style>
