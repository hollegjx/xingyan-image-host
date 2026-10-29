<template>
  <AccountShell title="偏好与设置" description="一点小调整，让这里更适合你。">
    <section class="account-panel" aria-label="浏览偏好">
      <div class="account-panel-heading">
        <h2>浏览偏好</h2>
        <span class="settings-local"><LaptopOutlined />仅此浏览器</span>
      </div>
      <div
        class="settings-preview"
        aria-hidden="true"
        :class="{ quiet: !preferences.particles, still: preferences.reduceMotion }"
      >
        <i v-for="index in 8" :key="index" :style="{ '--i': index }" />
        <div class="mini-window">
          <span /><span /><span />
          <div><b /><b /><b /></div>
        </div>
        <div>
          <strong>{{
            preferences.reduceMotion ? '安静一点，也很好。' : '让灵感，自然发生。'
          }}</strong>
          <p>留白之间，刚刚好的呼吸感。</p>
        </div>
      </div>
      <div class="setting-row">
        <div>
          <h3 id="particles-label">背景粒子</h3>
          <p id="particles-help">轻盈的粒子背景，随鼠标微微回应。</p>
        </div>
        <a-switch
          :checked="preferences.particles"
          aria-labelledby="particles-label"
          aria-describedby="particles-help"
          @change="(value: boolean | string | number) => preferences.setParticles(!!value)"
        />
      </div>
      <div class="setting-row">
        <div>
          <h3 id="motion-label">减少动态效果</h3>
          <p id="motion-help">停止粒子漂浮，减少界面中的浮动和过渡。</p>
        </div>
        <a-switch
          :checked="preferences.reduceMotion"
          aria-labelledby="motion-label"
          aria-describedby="motion-help"
          @change="(value: boolean | string | number) => preferences.setReduceMotion(!!value)"
        />
      </div>
      <div class="settings-saving">
        <span role="status"
          ><CheckOutlined />{{
            preferences.persistent
              ? '更改自动保存在当前浏览器，不会同步到其他设备。'
              : '浏览器存储不可用，更改仅在本次打开时有效。'
          }}</span
        ><button type="button" @click="preferences.reset">恢复默认</button>
      </div>
      <p class="account-note settings-system">我们也会遵循系统的“减少动态效果”设置。</p>
    </section>
    <section class="account-panel" aria-label="修改个人资料">
      <h2>修改个人资料</h2>
      <a-form :model="profileForm" layout="vertical" @finish="saveProfile">
        <a-form-item
          label="昵称"
          name="userName"
          :rules="[{ required: true, whitespace: true, max: 32, message: '昵称为1至32位' }]"
        >
          <a-input v-model:value="profileForm.userName" :maxlength="32" :disabled="!profileReady" />
        </a-form-item>
        <a-form-item label="个人简介" name="userProfile">
          <a-textarea
            v-model:value="profileForm.userProfile"
            :maxlength="128"
            show-count
            :rows="3"
            :disabled="!profileReady"
          />
        </a-form-item>
        <a-button
          type="primary"
          html-type="submit"
          :loading="savingProfile"
          :disabled="!profileReady"
          >保存资料</a-button
        >
      </a-form>
      <p v-if="profileError" role="alert" class="logout-error">{{ profileError }}</p>
      <a-button v-if="!profileReady" @click="loadProfile">重新读取资料</a-button>
      <p class="account-note settings-service">头像暂不支持自助修改，现有头像会保留。</p>
    </section>
    <section class="account-panel" aria-label="修改密码">
      <h2>修改密码</h2>
      <a-form :model="passwordForm" layout="vertical" @finish="savePassword">
        <a-form-item
          label="旧密码"
          name="oldPassword"
          :rules="[{ required: true, message: '请输入旧密码' }]"
        >
          <a-input-password
            v-model:value="passwordForm.oldPassword"
            autocomplete="current-password"
          />
        </a-form-item>
        <a-form-item
          label="新密码"
          name="newPassword"
          :rules="[{ required: true, min: 6, message: '新密码至少6位' }]"
        >
          <a-input-password v-model:value="passwordForm.newPassword" autocomplete="new-password" />
        </a-form-item>
        <a-form-item
          label="确认新密码"
          name="checkPassword"
          :rules="[{ required: true, message: '请再次输入新密码' }]"
        >
          <a-input-password
            v-model:value="passwordForm.checkPassword"
            autocomplete="new-password"
          />
        </a-form-item>
        <p class="account-note">
          新密码至少6位，UTF-8编码不超过72字节。修改成功后，其他设备和会话需要重新登录。
        </p>
        <a-button type="primary" html-type="submit" :loading="savingPassword">修改密码</a-button>
      </a-form>
      <p v-if="passwordError" role="alert" class="logout-error">{{ passwordError }}</p>
    </section>
    <section class="account-panel" aria-label="账户与登录">
      <div class="account-panel-heading">
        <h2>账户与登录</h2>
        <SafetyOutlined />
      </div>
      <div class="setting-row account-link">
        <div>
          <h3>个人资料</h3>
          <p>查看头像、昵称、账号和个人简介。</p>
        </div>
        <router-link to="/user/profile" aria-label="查看个人资料"
          >查看 <ArrowRightOutlined
        /></router-link>
      </div>
      <div class="setting-row">
        <div>
          <h3>退出当前登录</h3>
          <p>退出后，个人空间与投稿需要重新登录才能访问。</p>
        </div>
        <a-popconfirm
          title="确定退出当前登录？"
          ok-text="退出登录"
          cancel-text="留在这里"
          :ok-button-props="{ loading: loggingOut }"
          @confirm="logout"
        >
          <a-button :loading="loggingOut" aria-label="退出当前登录"
            ><LogoutOutlined />退出登录</a-button
          >
        </a-popconfirm>
      </div>
      <p v-if="logoutError" class="logout-error" role="alert">{{ logoutError }}</p>
      <p class="account-note settings-service">绑定邮箱和注销账户暂未开放。</p>
    </section>
  </AccountShell>
</template>
<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRightOutlined,
  CheckOutlined,
  LaptopOutlined,
  LogoutOutlined,
  SafetyOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import AccountShell from '@/components/AccountShell.vue'
import { usePreferencesStore } from '@/stores/usePreferencesStore'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import {
  userLogoutUsingPost,
  getLoginUserUsingGet,
  updateMyProfileUsingPost,
  updateMyPasswordUsingPost,
} from '@/service/api/userController'
const preferences = usePreferencesStore(),
  store = useLoginUserStore(),
  router = useRouter()
const loggingOut = ref(false),
  logoutError = ref('')
const profileForm = reactive<API.UserProfileUpdateRequest>({ userName: '', userProfile: '' })
const passwordForm = reactive<API.UserPasswordUpdateRequest>({
  oldPassword: '',
  newPassword: '',
  checkPassword: '',
})
const profileReady = ref(false),
  savingProfile = ref(false),
  savingPassword = ref(false)
const profileError = ref(''),
  passwordError = ref('')
async function loadProfile() {
  profileReady.value = false
  profileError.value = ''
  try {
    const res = await getLoginUserUsingGet()
    if (res.code !== 0 || !res.data?.id)
      throw new Error(res.message || '读取资料失败，请重新登录后重试')
    profileForm.userName = res.data.userName || ''
    profileForm.userProfile = res.data.userProfile || ''
    store.setLoginUser(res.data)
    profileReady.value = true
  } catch (e) {
    profileError.value = e instanceof Error ? e.message : '读取资料失败'
  }
}
async function saveProfile() {
  if (savingProfile.value || !profileReady.value) return
  profileError.value = ''
  if (
    !profileForm.userName.trim() ||
    profileForm.userName.trim().length > 32 ||
    (profileForm.userProfile?.length || 0) > 128
  ) {
    profileError.value = '昵称为1至32位，简介不超过128位'
    return
  }
  savingProfile.value = true
  try {
    const res = await updateMyProfileUsingPost({
      userName: profileForm.userName.trim(),
      userProfile: profileForm.userProfile,
    })
    if (res.code !== 0 || !res.data?.id) throw new Error(res.message || '保存失败')
    store.setLoginUser(res.data)
    message.success('资料已更新')
  } catch (e) {
    profileError.value = e instanceof Error ? e.message : '保存失败，请稍后重试'
  } finally {
    savingProfile.value = false
  }
}
async function savePassword() {
  if (savingPassword.value) return
  passwordError.value = ''
  if (
    !passwordForm.oldPassword ||
    passwordForm.newPassword.length < 6 ||
    new TextEncoder().encode(passwordForm.newPassword).length > 72
  ) {
    passwordError.value = '请输入旧密码；新密码至少6位，UTF-8编码不超过72字节'
    return
  }
  if (passwordForm.newPassword !== passwordForm.checkPassword) {
    passwordError.value = '两次密码输入不一致'
    return
  }
  savingPassword.value = true
  try {
    const res = await updateMyPasswordUsingPost({ ...passwordForm })
    if (res.code !== 0 || res.data !== true) throw new Error(res.message || '修改失败')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.checkPassword = ''
    message.success('密码已修改，其他会话需要重新登录')
  } catch (e) {
    passwordError.value = e instanceof Error ? e.message : '修改失败，请稍后重试'
  } finally {
    savingPassword.value = false
  }
}
onMounted(loadProfile)
async function logout() {
  if (loggingOut.value) return
  loggingOut.value = true
  logoutError.value = ''
  try {
    const res = await userLogoutUsingPost()
    if (res.code !== 0 || res.data !== true)
      throw new Error(res.message || '退出失败，请稍后重试。')
    store.clearLoginUser()
    message.success('已退出登录')
    await router.replace('/user/login')
  } catch (e) {
    logoutError.value = e instanceof Error ? e.message : '退出失败，请检查网络后重试。'
  } finally {
    loggingOut.value = false
  }
}
</script>
<style scoped>
.settings-local {
  display: flex;
  gap: 6px;
  color: var(--muted);
  font-size: 11px;
}
.settings-preview {
  position: relative;
  isolation: isolate;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 35px;
  min-height: 170px;
  border: 1px solid #f0f0f3;
  background: #fafafa;
  border-radius: 14px;
  margin: 4px 0 10px;
  overflow: hidden;
}
.settings-preview > i {
  position: absolute;
  z-index: -1;
  border-radius: 50%;
  background: #a0adb9;
  width: 3px;
  height: 3px;
  left: calc(var(--i) * 11%);
  top: 25%;
}
.settings-preview > i:nth-child(2n) {
  top: 75%;
}
.settings-preview > i:nth-child(3n) {
  top: 55%;
}
.settings-preview.quiet > i {
  display: none;
}
.settings-preview strong {
  font-size: 16px;
  font-weight: 550;
}
.settings-preview p {
  font-size: 11px;
  color: var(--muted);
  margin: 8px 0 0;
}
.mini-window {
  width: 125px;
  height: 86px;
  background: #fff;
  border: 1px solid #dddde3;
  border-radius: 9px;
  padding: 0 9px;
  box-shadow: 0 12px 24px #1d1d1f08;
  transform: rotate(-5deg);
}
.mini-window > span {
  display: inline-block;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #d2d2d7;
  margin: 0 3px 0 0;
}
.mini-window > div {
  display: flex;
  gap: 5px;
  padding-top: 6px;
  border-top: 1px solid #ededf0;
}
.mini-window b {
  display: block;
  background: #e7e7eb;
  border-radius: 4px;
  flex: 1;
  height: 42px;
}
.mini-window b:nth-child(2) {
  background: #c7c7ce;
}
.mini-window b:nth-child(3) {
  background: #a8a8b2;
}
.setting-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 23px 0;
  border-bottom: 1px solid var(--line);
}
.setting-row h3 {
  font-size: 13px;
  font-weight: 550;
  margin: 0 0 5px;
}
.setting-row p {
  color: var(--muted);
  font-size: 12px;
  margin: 0;
}
.setting-row :deep(.ant-switch),
.setting-row :deep(.ant-btn) {
  flex-shrink: 0;
}
.settings-saving {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
  padding-top: 20px;
  font-size: 11px;
  color: var(--muted);
}
.settings-saving .anticon {
  margin-right: 5px;
}
.settings-saving button {
  background: none;
  border: 0;
  cursor: pointer;
  color: var(--ink);
  padding: 0;
  font-size: 11px;
  white-space: nowrap;
}
.settings-system {
  margin: 8px 0 0;
  font-size: 10px !important;
}
.account-link {
  padding-top: 0;
}
.account-link > a {
  font-size: 12px;
  white-space: nowrap;
}
.account-link > a .anticon {
  margin-left: 6px;
}
.settings-service {
  margin: 20px 0 0;
  font-size: 11px !important;
}
.logout-error {
  color: #b64f4f;
  font-size: 12px;
  margin-bottom: 0;
}
@media (max-width: 560px) {
  .settings-preview {
    gap: 18px;
    min-height: 140px;
  }
  .mini-window {
    width: 90px;
    height: 70px;
    flex-shrink: 0;
  }
  .mini-window b {
    height: 28px;
  }
  .settings-preview strong {
    font-size: 13px;
  }
  .settings-preview p {
    font-size: 9px;
  }
  .settings-saving {
    flex-direction: column;
    gap: 12px;
  }
  .setting-row {
    gap: 14px;
  }
}
@media (max-width: 380px) {
  .settings-preview {
    flex-direction: column;
    padding: 24px 12px;
    text-align: center;
  }
}
</style>
