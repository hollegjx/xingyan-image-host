<template>
  <section class="invite-page">
    <PageHeader eyebrow="团队空间" title="加入团队" description="确认邀请信息后再加入团队" />
    <div class="account-panel">
      <template v-if="!store.loginUser.id">
        <p>请先登录，再查看邀请并确认是否加入。</p>
        <a-button type="primary" @click="login">登录后继续</a-button>
      </template>
      <a-spin v-else-if="loading" />
      <template v-else-if="invitation">
        <h2>{{ invitation.spaceName }}</h2>
        <p>加入后的角色：{{ roleName(invitation.spaceRole) }}</p>
        <p>有效期至：{{ new Date(invitation.expiresAt).toLocaleString() }}</p>
        <a-button type="primary" :loading="joining" @click="join">确认加入</a-button>
      </template>
      <a-alert v-if="error" type="error" :message="error" show-icon />
      <a-button v-if="error && store.loginUser.id" @click="load">重新检查邀请</a-button>
    </div>
  </section>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { previewInvitation, joinInvitation, type Invitation } from '@/service/api/teamInvitationController'
import { roleName } from '@/utils/space'
const route = useRoute(), router = useRouter(), store = useLoginUserStore()
const invitation = ref<Invitation>(), loading = ref(false), joining = ref(false), error = ref('')
let sequence = 0
function token() { return new URLSearchParams(route.hash.slice(1)).get('token') || '' }
function login() {
  // 凭据只保留在 fragment；redirect 的查询参数只包含固定路由。
  void router.push({ path: '/user/login', query: { redirect: '/space/invite' }, hash: route.hash })
}
async function load() {
  const ticket = ++sequence; invitation.value = undefined; error.value = ''
  if (!store.loginUser.id) return
  loading.value = true
  try {
    if (!/^[A-Za-z0-9_-]{43}$/.test(token())) throw new Error('邀请链接无效')
    const res = await previewInvitation(token())
    if (ticket !== sequence) return
    if (res.code !== 0 || !res.data) throw new Error(res.message || '无法读取邀请')
    invitation.value = res.data
  } catch (e) { if (ticket === sequence) error.value = e instanceof Error ? e.message : '无法读取邀请，请稍后重试' }
  finally { if (ticket === sequence) loading.value = false }
}
async function join() {
  if (joining.value || !invitation.value || !store.loginUser.id) return
  joining.value = true; error.value = ''
  try {
    const res = await joinInvitation(token())
    if (res.code !== 0 || !res.data) throw new Error(res.message || '加入失败')
    await router.replace(`/space/${res.data}`)
  } catch (e) { error.value = e instanceof Error ? e.message : '结果未确认，请到团队列表核对后重试'; invitation.value = undefined }
  finally { joining.value = false }
}
watch(() => [route.hash, store.loginUser.id], load, { immediate: true })
</script>
<style scoped>
.invite-page { max-width: 720px; margin: 32px auto; }
.invite-page .ant-alert { margin: 16px 0; }
</style>
