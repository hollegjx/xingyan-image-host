<template>
  <AccountShell title="个人资料" description="认识你的创作身份，也找到属于你的作品。">
    <div v-if="error" class="account-panel profile-error" role="alert">
      <h2>暂时无法读取资料</h2>
      <p class="account-note">{{ error }}</p>
      <a-button @click="loadProfile">重新加载</a-button>
    </div>
    <div v-else-if="loading" class="account-panel" aria-busy="true">
      <a-skeleton :paragraph="{ rows: 5 }" />
    </div>
    <template v-else-if="profile">
      <section class="account-panel profile-identity" aria-label="我的身份">
        <a-avatar :src="profile.userAvatar || undefined" :size="80" class="profile-avatar">{{
          profile.userName?.slice(0, 1) || '我'
        }}</a-avatar>
        <div>
          <span class="profile-welcome">很高兴在这里遇见你</span>
          <h2>{{ profile.userName || '尚未设置昵称' }}</h2>
          <p>{{ profile.userProfile || '还没有个人简介，作品也会替你表达。' }}</p>
          <span class="profile-role"
            ><UserOutlined />{{ profile.userRole === 1 ? '管理员' : '普通用户' }}</span
          >
        </div>
      </section>
      <section class="account-panel" aria-label="账户资料">
        <div class="account-panel-heading">
          <h2>账户资料</h2>
          <a-button type="text" size="small" aria-label="刷新个人资料" @click="loadProfile"
            ><ReloadOutlined />刷新</a-button
          >
        </div>
        <dl class="profile-fields">
          <div>
            <dt>昵称</dt>
            <dd>{{ profile.userName || '未设置' }}</dd>
          </div>
          <div>
            <dt>登录账号</dt>
            <dd>{{ profile.userAccount || '未提供' }}</dd>
          </div>
          <div>
            <dt>用户 ID</dt>
            <dd class="profile-id">{{ profile.id }}</dd>
          </div>
          <div>
            <dt>加入时间</dt>
            <dd>{{ displayDate(profile.createTime) }}</dd>
          </div>
          <div class="profile-bio">
            <dt>个人简介</dt>
            <dd>{{ profile.userProfile || '未填写个人简介' }}</dd>
          </div>
        </dl>
        <p class="profile-service-note">
          <LockOutlined /> 当前资料由管理员维护，暂不支持自助修改昵称、头像或密码。
        </p>
      </section>
      <div class="profile-shortcuts">
        <router-link to="/picture/mine"
          ><span class="shortcut-icon"><PictureOutlined /></span>
          <div>
            <strong>我的投稿</strong>
            <p>查看审核进度，继续完善作品</p>
          </div>
          <ArrowRightOutlined
        /></router-link>
        <router-link to="/space/mine"
          ><span class="shortcut-icon"><FolderOutlined /></span>
          <div>
            <strong>我的空间</strong>
            <p>收藏与整理，不必公开分享</p>
          </div>
          <ArrowRightOutlined
        /></router-link>
      </div>
    </template>
  </AccountShell>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import {
  ArrowRightOutlined,
  FolderOutlined,
  LockOutlined,
  PictureOutlined,
  ReloadOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import AccountShell from '@/components/AccountShell.vue'
import { getLoginUserUsingGet } from '@/service/api/userController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { displayDate } from '@/utils/submissions'
const store = useLoginUserStore()
const profile = ref<API.LoginUserVO>(),
  loading = ref(true),
  error = ref('')
let sequence = 0
async function loadProfile() {
  const current = ++sequence
  loading.value = true
  error.value = ''
  profile.value = undefined
  try {
    const res = await getLoginUserUsingGet()
    if (current !== sequence) return
    if (res.code !== 0 || !res.data?.id) throw new Error(res.message || '请检查登录状态后重试。')
    profile.value = res.data
    store.setLoginUser(res.data)
  } catch (e) {
    if (current === sequence)
      error.value = e instanceof Error ? e.message : '网络连接异常，请稍后重试。'
  } finally {
    if (current === sequence) loading.value = false
  }
}
onMounted(loadProfile)
onBeforeUnmount(() => {
  sequence++
})
</script>
<style scoped>
.profile-identity {
  display: flex;
  align-items: center;
  gap: 26px;
}
.profile-avatar {
  flex-shrink: 0;
  background: #f0f0f2;
  color: var(--ink);
  font-size: 30px;
}
.profile-identity > div {
  min-width: 0;
}
.profile-welcome {
  font-size: 11px;
  color: var(--muted);
}
.profile-identity h2 {
  font-size: 25px !important;
  margin: 6px 0 8px !important;
  overflow-wrap: anywhere;
}
.profile-identity p {
  font-size: 12px;
  color: var(--muted);
  margin: 0 0 12px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.profile-role {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  background: var(--surface-soft);
  border-radius: 100px;
  padding: 5px 10px;
  font-size: 10px;
}
.profile-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0;
  gap: 26px 32px;
}
.profile-fields dt {
  font-size: 11px;
  color: var(--muted);
  margin-bottom: 8px;
}
.profile-fields dd {
  margin: 0;
  font-size: 14px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.profile-id {
  font-variant-numeric: tabular-nums;
  font-size: 12px !important;
}
.profile-bio {
  grid-column: 1 / -1;
}
.profile-service-note {
  display: flex;
  align-items: baseline;
  gap: 8px;
  color: var(--muted);
  font-size: 11px;
  border-top: 1px solid var(--line);
  padding-top: 20px;
  margin: 24px 0 0;
}
.profile-shortcuts {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
}
.profile-shortcuts > a {
  display: flex;
  gap: 15px;
  align-items: center;
  border: 1px solid var(--line);
  border-radius: 18px;
  padding: 22px;
  background: #fff;
  transition:
    transform 0.25s,
    box-shadow 0.25s;
}
.profile-shortcuts > a > div {
  flex: 1;
}
.profile-shortcuts strong {
  font-size: 13px;
  font-weight: 550;
}
.profile-shortcuts p {
  font-size: 11px;
  color: var(--muted);
  margin: 5px 0 0;
}
.shortcut-icon {
  display: grid;
  place-items: center;
  background: #f5f5f7;
  border-radius: 12px;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  font-size: 18px;
}
.profile-shortcuts > a > .anticon {
  font-size: 12px;
}
.profile-error {
  text-align: center;
  padding: 48px !important;
}
@media (hover: hover) and (prefers-reduced-motion: no-preference) {
  .profile-shortcuts > a:hover {
    transform: translateY(-3px);
    box-shadow: 0 7px 20px #1d1d1f08;
  }
}
@media (max-width: 640px) {
  .profile-shortcuts {
    grid-template-columns: 1fr;
    gap: 14px;
  }
  .profile-identity {
    align-items: flex-start;
    gap: 18px;
  }
}
@media (max-width: 380px) {
  .profile-identity {
    flex-direction: column;
  }
  .profile-fields {
    grid-template-columns: 1fr;
    gap: 22px;
  }
}
</style>
