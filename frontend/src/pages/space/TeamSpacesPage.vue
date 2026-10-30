<template>
  <section class="team-page">
    <header class="team-header">
      <div>
        <span class="eyebrow">协作 · 共享 · 共创</span>
        <h1>团队空间<span class="title-period">。</span></h1>
        <p>
          和伙伴一起整理素材。浏览者只能查看；编辑者可上传、编辑和删除图片；团队管理员还能管理成员和分析图库。
        </p>
      </div>
      <div class="team-header-actions">
        <a-button :loading="loading" aria-label="刷新" @click="load">
          <template #icon><ReloadOutlined /></template>
          刷新
        </a-button>
        <a-button type="primary" aria-label="创建团队空间" @click="creatingOpen = true">
          <template #icon><PlusOutlined /></template>
          创建团队空间
        </a-button>
      </div>
    </header>

    <div v-if="user.loginUser.id" class="team-id-strip">
      <TeamOutlined />
      <span>想加入别人的团队？把你的用户 ID 发给团队管理员</span>
      <button type="button" class="team-id" title="复制用户 ID" @click="copyUserId">
        {{ user.loginUser.id }}<CopyOutlined />
      </button>
    </div>

    <a-alert v-if="error" type="error" :message="error" show-icon />
    <a-spin :spinning="loading">
      <a-empty
        v-if="!loading && !error && !teams.length"
        class="team-empty"
        description="尚未加入团队，请创建团队或请团队管理员按你的用户 ID 添加"
      />
      <div class="teams">
        <article v-for="member in teams" :key="String(member.id)" class="team-card">
          <div class="team-card-head">
            <span class="team-mark" aria-hidden="true">{{
              (member.space?.spaceName || '团').slice(0, 1)
            }}</span>
            <div class="team-card-title">
              <h2>{{ member.space?.spaceName || '未命名团队' }}</h2>
              <div class="team-roles">
                <span class="team-role">{{ roleName(member.spaceRole) }}</span>
                <span v-if="member.owner" class="team-role team-role--owner">创建者</span>
              </div>
            </div>
          </div>
          <SpaceUsage v-if="member.space" :space="member.space" />
          <div class="team-card-foot">
            <span class="team-space-id">空间 ID：{{ member.spaceId }}</span>
            <div class="team-card-actions">
              <a-button
                v-if="hasPermission(member.space, permissions.members)"
                @click="router.push(`/space/${member.spaceId}/members`)"
                >成员管理</a-button
              >
              <a-button
                v-if="hasPermission(member.space, permissions.view)"
                type="primary"
                @click="router.push(`/space/${member.spaceId}`)"
                >进入团队</a-button
              >
            </div>
          </div>
        </article>
      </div>
      <a-pagination
        v-if="total > 12"
        :current="current"
        :page-size="12"
        :total="total"
        :show-size-changer="false"
        @change="changePage"
      />
    </a-spin>
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
import { CopyOutlined, PlusOutlined, ReloadOutlined, TeamOutlined } from '@ant-design/icons-vue'
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
async function copyUserId() {
  try {
    await navigator.clipboard.writeText(String(user.loginUser.id))
    message.success('已复制用户 ID')
  } catch {
    message.info('复制失败，请手动选择用户 ID')
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
.team-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin: 40px 0 28px;
}
.team-header .eyebrow {
  color: var(--muted);
  letter-spacing: 0.15em;
}
.team-header h1 {
  margin: 14px 0 12px;
  font-size: clamp(34px, 4vw, 46px);
  font-weight: 650;
  line-height: 1.2;
}
.title-period {
  color: #b7b7bf;
}
.team-header p {
  max-width: 640px;
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}
.team-header-actions {
  display: flex;
  flex: none;
  gap: 10px;
}

.team-id-strip {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 10px;
  padding: 14px 0;
  border-block: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}
.team-id {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border: 0;
  border-radius: 999px;
  background: var(--surface-soft);
  color: var(--ink);
  font: inherit;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
}
.team-id:hover {
  background: #ececf0;
}

.team-empty {
  margin: 56px 0;
}
.teams {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
  margin: 28px 0;
}
.team-card {
  display: flex;
  flex-direction: column;
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}
.team-card:hover {
  border-color: #d2d2d7;
  box-shadow: 0 10px 30px rgb(0 0 0 / 5%);
}
.team-card-head {
  display: flex;
  align-items: center;
  gap: 14px;
}
.team-mark {
  display: grid;
  flex: none;
  width: 44px;
  height: 44px;
  place-items: center;
  border-radius: 14px;
  background: var(--ink);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}
.team-card-title {
  min-width: 0;
}
.team-card-title h2 {
  margin: 0 0 6px;
  overflow: hidden;
  font-size: 18px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.team-roles {
  display: flex;
  gap: 6px;
}
.team-role {
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--surface-soft);
  color: var(--muted);
  font-size: 12px;
}
.team-role--owner {
  background: #ececf0;
  color: var(--ink);
}
.team-card :deep(.space-usage) {
  max-width: none;
  margin: 22px 0 0;
}
.team-card :deep(.space-usage p) {
  font-size: 12px;
  color: var(--muted);
}
.team-card-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: auto;
  padding-top: 20px;
}
.team-space-id {
  color: var(--muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}
.team-card-actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 720px) {
  .team-header {
    flex-direction: column;
    align-items: flex-start;
    margin-top: 24px;
  }
  .team-header h1 {
    font-size: 32px;
  }
  .teams {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
