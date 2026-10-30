<template>
  <AuthShell>
    <div id="userLoginPage">
      <h1>欢迎回来</h1>
      <p class="auth-subtitle">回到星檐，接着收好你的日常。</p>
      <p v-if="designPreview" class="preview-warning">
        当前为本地只读预览，登录不会提交。请勿输入真实账号或密码。
      </p>
      <a-form :model="formState" name="login" layout="vertical" @finish="handleSubmit">
        <a-form-item
          label="账号"
          name="userAccount"
          :rules="[{ required: true, message: '请输入账号!' }]"
        >
          <a-input
            v-model:value="formState.userAccount"
            placeholder="请输入账号"
            autocomplete="username"
          />
        </a-form-item>
        <a-form-item
          label="密码"
          name="userPassword"
          :rules="[{ required: true, message: '请输入密码!' }]"
        >
          <a-input-password
            v-model:value="formState.userPassword"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </a-form-item>
        <!-- 登录失败的原因留在表单里，不会像气泡提示那样一闪而过 -->
        <p v-if="loginError" class="login-error" role="alert">{{ loginError }}</p>

        <a-form-item
          ><a-button
            type="primary"
            html-type="submit"
            class="auth-submit"
            :loading="submitting"
            :disabled="designPreview"
            aria-label="登录"
            >登录</a-button
          ></a-form-item
        >
      </a-form>
      <p class="auth-switch">还没有账号？<RouterLink to="/user/register">去注册</RouterLink></p>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { message } from 'ant-design-vue'
import AuthShell from '@/components/AuthShell.vue'
import { userLoginUsingPost } from '@/service/api/userController'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
const router = useRouter()
const route = useRoute(),
  loginUserStore = useLoginUserStore()
const designPreview = import.meta.env.DEV && import.meta.env.MODE === 'design'
const submitting = ref(false)
const loginError = ref('')
const formState = reactive<API.UserLoginRequest>({ userAccount: '', userPassword: '' })
// 重新输入时清掉上一次的错误
watch(
  () => [formState.userAccount, formState.userPassword],
  () => {
    loginError.value = ''
  },
)
async function handleSubmit(values: API.UserLoginRequest) {
  if (designPreview || submitting.value) return

  loginError.value = ''
  submitting.value = true
  try {
    const res = await userLoginUsingPost(values)
    if (res.code !== 0) throw new Error(res.message || '登录失败')
    message.success('登录成功')
    await loginUserStore.fetchLoginUser()
    const target = route.query.redirect
    await router.replace(
      typeof target === 'string' && /^\/(?!\/)/.test(target) && !/[\\\r\n]/.test(target)
        ? target
        : '/',
    )
  } catch (e) {
    loginError.value = e instanceof Error ? e.message : '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-error {
  margin: -8px 0 16px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #fff2f0;
  color: #cf1322;
  font-size: 13px;
  line-height: 1.6;
}
</style>
