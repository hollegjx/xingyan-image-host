<template>
  <section class="team-page">
    <h2>团队空间</h2>
    <p>
      加入的团队会显示在这里。浏览者只能查看；编辑者可上传、编辑和删除图片；团队管理员还能管理成员和分析图库。
    </p>
    <a-space wrap>
      <a-button type="primary" @click="creatingOpen = true">创建团队空间</a-button>
      <a-button :loading="loading" @click="load">刷新</a-button>
    </a-space>
    <a-alert v-if="error" type="error" :message="error" show-icon />
    <a-spin :spinning="loading">
      <a-empty
        v-if="!loading && !error && !teams.length"
        description="尚未加入团队，请创建团队或请团队管理员按你的用户 ID 添加"
      />
      <div class="teams">
        <a-card v-for="member in teams" :key="String(member.id)" :title="member.space?.spaceName">
          <a-tag>{{ roleName(member.spaceRole) }}</a-tag>
          <a-tag v-if="member.owner">创建者</a-tag>
          <p>空间 ID：{{ member.spaceId }}</p>
          <SpaceUsage v-if="member.space" :space="member.space" />
          <a-space wrap>
            <a-button
              v-if="hasPermission(member.space, permissions.view)"
              type="primary"
              @click="router.push(`/space/${member.spaceId}`)"
              >进入团队</a-button
            >
            <a-button
              v-if="hasPermission(member.space, permissions.members)"
              @click="router.push(`/space/${member.spaceId}/members`)"
              >成员管理</a-button
            >
          </a-space>
        </a-card>
      </div>
      <a-pagination
        v-if="total"
        :current="current"
        :page-size="12"
        :total="total"
        :show-size-changer="false"
        @change="changePage"
      />
    </a-spin>
    <p>我的用户 ID：{{ user.loginUser.id }}</p>
    <a-modal
      v-model:open="creatingOpen"
      title="创建团队空间"
      :confirm-loading="saving"
      @ok="create"
    >
      <a-form layout="vertical">
        <a-form-item label="空间名称"
          ><a-input v-model:value="name" :maxlength="30" placeholder="例如：产品素材团队"
        /></a-form-item>
      </a-form>
      <p>
        创建普通版团队空间，每位普通用户可创建一个团队；加入其他团队不占用创建名额。创建后你自动成为团队管理员。
      </p>
    </a-modal>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { listMyTeamsUsingPost } from '@/service/api/spaceUserController'
import { addSpaceUsingPost } from '@/service/api/spaceController'
import { hasPermission, permissions, parseId, roleName } from '@/utils/space'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import SpaceUsage from '@/components/SpaceUsage.vue'

const router = useRouter()
const user = useLoginUserStore()
const teams = ref<API.SpaceUserVO[]>([])
const current = ref(1),
  total = ref(0)
const loading = ref(false),
  saving = ref(false),
  creatingOpen = ref(false)
const error = ref(''),
  name = ref('')
let sequence = 0
async function load() {
  const ticket = ++sequence
  loading.value = true
  error.value = ''
  teams.value = []
  try {
    const res = await listMyTeamsUsingPost({ current: current.value, pageSize: 12 })
    if (ticket !== sequence) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '获取团队失败')
    teams.value = res.data.records || []
    total.value = Number(res.data.total || 0)
  } catch (e) {
    if (ticket === sequence) error.value = e instanceof Error ? e.message : '获取团队失败'
  } finally {
    if (ticket === sequence) loading.value = false
  }
}
function changePage(page: number) {
  current.value = page
  void load()
}
async function create() {
  if (saving.value) return
  if (!name.value.trim()) {
    message.warning('请输入空间名称')
    return
  }
  saving.value = true
  try {
    const res = await addSpaceUsingPost({
      spaceName: name.value.trim(),
      spaceType: 1,
      spaceLevel: 0,
    })
    const id = parseId(res.data)
    if (res.code !== 0 || !id) throw new Error(res.message || '创建失败')
    creatingOpen.value = false
    message.success('团队创建成功')
    await router.push(`/space/${id}/members`)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '创建结果未确认，请刷新团队列表后再试')
  } finally {
    saving.value = false
  }
}
onMounted(load)
onBeforeUnmount(() => {
  sequence++
})
</script>

<style scoped>
.team-page {
  max-width: 1100px;
  padding: 24px;
  margin: auto;
}
.teams {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 16px;
  margin: 20px 0;
}
.team-page > p {
  color: #666;
  overflow-wrap: anywhere;
}
</style>
