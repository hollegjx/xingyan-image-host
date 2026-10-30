<template>
  <div id="globalHeader">
    <a href="#main-content" class="skip-link">跳到主要内容</a>
    <aside class="sidebar">
      <router-link to="/" class="brand" aria-label="星檐图床首页">
        <BrandMark class="brand-mark" />
        <div><strong>星檐图床</strong><small>XINGYAN · IMAGES</small></div>
      </router-link>
      <nav class="side-nav" aria-label="主导航">
        <div v-for="group in groups" :key="group.label" class="nav-group">
          <p>{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :class="['nav-item', { active: activePath === item.path }]"
            :aria-current="activePath === item.path ? 'page' : undefined"
          >
            <component :is="item.icon" /><span>{{ item.label }}</span>
          </router-link>
        </div>
      </nav>
      <div class="sidebar-bottom">
        <a
          class="source-link"
          href="https://github.com/hollegjx"
          target="_blank"
          rel="noopener noreferrer"
          ><GithubOutlined /> 项目主页 <ArrowUpOutlined
        /></a>
      </div>
    </aside>
    <header class="topbar">
      <div class="topbar-left">
        <a-button class="mobile-menu" aria-label="打开导航" @click="menuOpen = true"
          ><MenuOutlined
        /></a-button>
        <router-link to="/" class="mobile-brand" aria-label="星檐图床首页"
          ><BrandMark /> <strong>星檐图床</strong></router-link
        >
        <div class="breadcrumb">
          <span>工作台</span><span class="slash">/</span><strong>{{ pageTitle }}</strong>
        </div>
      </div>
      <div class="topbar-actions">
        <router-link v-if="showGlobalUpload" to="/picture/add" class="upload-link"
          ><PlusOutlined /><span>创建图片</span></router-link
        >
        <span v-if="showGlobalUpload" class="action-divider" />
        <a-dropdown v-if="loginUser.id" :trigger="['click']">
          <button class="user-info" type="button" aria-label="用户菜单">
            <a-avatar v-if="loginUser.userAvatar" :src="loginUser.userAvatar" :size="32" />
            <span v-else class="user-avatar">{{ loginUser.userName?.slice(0, 1) || '我' }}</span>
            <span class="user-name">{{ loginUser.userName || '我的账户' }}</span
            ><DownOutlined class="user-chevron" />
          </button>
          <template #overlay>
            <a-menu class="account-dropdown">
              <a-menu-item key="profile" @click="router.push('/user/profile')"
                ><UserOutlined /> 个人资料</a-menu-item
              >
              <a-menu-item key="settings" @click="router.push('/user/settings')"
                ><SettingOutlined /> 偏好与设置</a-menu-item
              >
              <a-menu-divider />
              <a-menu-item key="pictures" @click="router.push('/picture/mine')"
                ><PictureOutlined /> 我的投稿</a-menu-item
              >
              <a-menu-divider />
              <a-menu-item key="logout" :disabled="loggingOut" @click="handleLogout"
                ><LogoutOutlined /> {{ loggingOut ? '正在退出…' : '退出登录' }}</a-menu-item
              >
            </a-menu>
          </template>
        </a-dropdown>
        <a v-else :href="router.resolve('/user/login').href" class="upload-link"
          >登录 / 注册 <ArrowRightOutlined
        /></a>
      </div>
    </header>
    <a-drawer v-model:open="menuOpen" placement="left" title="星檐图床" :width="280">
      <nav aria-label="移动导航" class="drawer-nav">
        <div v-for="group in groups" :key="group.label" class="nav-group">
          <p>{{ group.label }}</p>
          <router-link
            v-for="item in group.items"
            :key="item.path"
            :to="item.path"
            :class="['nav-item', { active: activePath === item.path }]"
            :aria-current="activePath === item.path ? 'page' : undefined"
            @click="menuOpen = false"
            ><component :is="item.icon" />{{ item.label }}</router-link
          >
        </div>
      </nav>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  AppstoreOutlined,
  PictureOutlined,
  FolderOutlined,
  TeamOutlined,
  UserOutlined,
  BarChartOutlined,
  SettingOutlined,
  PlusOutlined,
  MenuOutlined,
  DownOutlined,
  GithubOutlined,
  ArrowUpOutlined,
  ArrowRightOutlined,
  LogoutOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { storeToRefs } from 'pinia'
import BrandMark from '@/components/BrandMark.vue'
import { brandName, pageTitles } from '@/utils/brand'
import { galleryOrigin } from '@/utils/galleryNavigation'
import { useNavigationStore } from '@/stores/useNavigationStore'
import { parseId } from '@/utils/space'
import { getSpaceVoUsingGet } from '@/service/api/spaceController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { userLogoutUsingPost } from '@/service/api/userController'

const router = useRouter(),
  route = useRoute()
const store = useLoginUserStore()
const navigation = useNavigationStore()
watch(
  () => [route.name, route.params.id],
  async () => {
    const id = parseId(route.params.id)
    // Analysis may be opened directly, without first visiting the space gallery.
    if (route.name !== 'space-analyze' || !id || navigation.spaceTypes[id] !== undefined) return
    try {
      const response = await getSpaceVoUsingGet({ id })
      if (
        route.name === 'space-analyze' &&
        parseId(route.params.id) === id &&
        response.code === 0 &&
        response.data
      )
        navigation.rememberSpace(response.data)
    } catch {
      /* Unknown scope stays unselected; never guess a private/team label. */
    }
  },
  { immediate: true },
)
const { loginUser } = storeToRefs(store)
const menuOpen = ref(false)
const loggingOut = ref(false)
const showGlobalUpload = computed(
  () =>
    loginUser.value.id &&
    !['space-detail', 'my-pictures', 'user-profile', 'user-settings'].includes(String(route.name)),
)
const groups = computed(() => [
  { label: '发现', items: [{ path: '/', label: '探索图库', icon: AppstoreOutlined }] },
  ...(loginUser.value.id
    ? [
        {
          label: '我的工作台',
          items: [
            { path: '/picture/mine', label: '我的投稿', icon: PictureOutlined },
            { path: '/space/mine', label: '我的空间', icon: FolderOutlined },
            { path: '/space/teams', label: '团队空间', icon: TeamOutlined },
          ],
        },
      ]
    : []),
  ...(loginUser.value.id && loginUser.value.userRole === 1
    ? [
        {
          label: '管理',
          items: [
            { path: '/admin/pictureManage', label: '图片管理', icon: PictureOutlined },
            { path: '/admin/userManage', label: '用户管理', icon: UserOutlined },
            { path: '/admin/spaceManage', label: '空间管理', icon: SettingOutlined },
            { path: '/admin/spaceAnalyze', label: '图库分析', icon: BarChartOutlined },
          ],
        },
      ]
    : []),
])
const activePath = computed(() => {
  function spaceSection(id?: string) {
    const type = id ? navigation.spaceTypes[id] : undefined
    return type === 1 ? '/space/teams' : type === 0 ? '/space/mine' : ''
  }
  if (route.name === 'my-space') return '/space/mine'
  if (route.name === 'collaborate-picture') return '/space/teams'
  if (route.name === 'team-spaces' || route.name === 'space-members') return '/space/teams'
  if (route.path.startsWith('/space/')) return spaceSection(parseId(route.params.id))
  if (['picture-detail', 'add-picture', 'collaborate-picture'].includes(String(route.name))) {
    const id = parseId(route.params.id) || parseId(route.query.id)
    const sid = parseId(route.query.spaceId) || (id ? navigation.pictureSpaces[id] : undefined)
    if (sid) return spaceSection(sid)
    const origin = galleryOrigin()?.path.split(/[?#]/)[0]
    if (origin === '/picture/mine') return '/picture/mine'
    if (origin?.startsWith('/space/')) return spaceSection(origin.split('/')[2])
    return route.name === 'add-picture' ? '/picture/mine' : '/'
  }
  return route.path
})
const pageTitle = computed(() => {
  if (route.name === 'add-picture' && route.query.id) return '编辑图片'
  return pageTitles[String(route.name)] || brandName
})
async function handleLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    const res = await userLogoutUsingPost()
    if (res.code !== 0) throw new Error(res.message || '退出失败，请稍后重试')
    store.clearLoginUser()
    message.success('已退出登录')
    await router.push('/user/login')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '退出登录失败，请稍后重试')
  } finally {
    loggingOut.value = false
  }
}
</script>

<style scoped>
.account-dropdown {
  min-width: 190px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 14px;
}
.account-dropdown :deep(.ant-dropdown-menu-item) {
  padding: 10px 12px;
  font-size: 12px;
}
.account-dropdown :deep(.anticon) {
  margin-right: 8px;
  color: var(--muted);
}
.skip-link {
  position: fixed;
  top: -100px;
  left: 20px;
  z-index: 2000;
  background: white;
  padding: 12px;
}
.skip-link:focus {
  top: 10px;
}
.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  width: var(--sidebar-width);
  background: #fafafa;
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  z-index: 30;
  overflow-y: auto;
}
.brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 28px 24px 48px;
  color: var(--ink);
  text-decoration: none;
}
.brand-mark {
  width: 36px;
  flex-shrink: 0;
}
.brand strong {
  display: block;
  font-size: 21px;
  font-weight: 650;
  line-height: 1.3;
  letter-spacing: -0.8px;
}
.brand small {
  display: block;
  font-size: 7.5px;
  letter-spacing: 1px;
  white-space: nowrap;
  margin-top: 5px;
  color: var(--muted);
}
.side-nav {
  padding: 0 16px;
}
.nav-group {
  margin-bottom: 28px;
}
.nav-group > p {
  margin: 0 12px 11px;
  font-size: 11px;
  color: var(--muted);
  letter-spacing: 0.04em;
}
.nav-item {
  display: flex;
  gap: 13px;
  align-items: center;
  min-height: 43px;
  margin-bottom: 6px;
  padding: 0 14px;
  border-radius: 10px;
  font-size: 13px;
  color: var(--muted);
  transition:
    transform var(--motion-duration) var(--motion-ease),
    background 0.16s,
    color 0.16s;
}
.nav-item > .anticon {
  font-size: 17px;
}
.nav-item:hover {
  background: #f0f0f2;
  color: var(--accent);
}
.nav-item.active {
  background: #eaeaed;
  color: var(--ink);
  font-weight: 600;
}
.sidebar-bottom {
  margin-top: auto;
  padding: 28px 22px 22px;
}
.source-link {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: var(--muted);
  margin: 21px 3px 0;
}
.source-link .anticon:last-child {
  margin-left: auto;
  transform: rotate(45deg);
  font-size: 10px;
}
.topbar {
  position: fixed;
  top: 0;
  right: 0;
  left: var(--sidebar-width);
  height: 78px;
  border-bottom: 1px solid var(--line);
  background: #ffffffdc;
  backdrop-filter: blur(20px);
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 42px;
  gap: 16px;
  z-index: 25;
}
.topbar-left,
.topbar-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 13px;
  font-size: 12px;
  white-space: nowrap;
}
.breadcrumb > span {
  color: var(--muted);
}
.breadcrumb .slash {
  color: #d2d2d7;
}
.breadcrumb strong {
  font-weight: 500;
}
.upload-link {
  display: flex;
  gap: 7px;
  align-items: center;
  padding: 10px 18px;
  border-radius: 100px;
  background: var(--accent);
  color: white;
  font-size: 12px;
  white-space: nowrap;
}
.upload-link:hover {
  background: #3a3a3c;
  color: white;
}
.action-divider {
  height: 24px;
  border-left: 1px solid var(--line);
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  border: 0;
  background: transparent;
  padding: 4px 0;
  cursor: pointer;
}
.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ededf0;
  color: var(--muted);
  display: grid;
  place-items: center;
  font-size: 12px;
}
.user-name {
  font-size: 12px;
  color: var(--ink);
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-chevron {
  font-size: 10px;
  color: var(--muted);
}
.mobile-menu,
.mobile-brand {
  display: none;
}
@media (max-width: 1100px) {
  .topbar {
    padding: 0 28px;
  }
}
@media (max-width: 800px) {
  .sidebar,
  .breadcrumb {
    display: none;
  }
  .topbar {
    left: 0;
    height: 68px;
    padding: 0 20px;
  }
  .mobile-menu {
    display: inline-flex;
    align-items: center;
    padding: 0;
    width: 30px;
    border: 0;
    background: transparent;
  }
  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 15px;
  }
  .mobile-brand svg {
    width: 27px;
  }
  .topbar-left,
  .topbar-actions {
    gap: 12px;
  }
  .user-name,
  .user-chevron,
  .action-divider {
    display: none;
  }
  .upload-link {
    padding: 8px 12px;
    font-size: 11px;
  }
}
@media (max-width: 380px) {
  .topbar {
    padding: 0 12px;
  }
  .topbar-actions {
    gap: 8px;
  }
  .mobile-brand {
    gap: 5px;
  }
}
</style>
