<template>
  <AuthShell>
    <div id="userRegisterPage">
      <h1>在星檐，留一处空间。</h1>
      <p class="auth-subtitle">创建账户，从第一张想留下的图片开始。</p>
      <p v-if="designPreview" class="preview-warning">
        当前为本地只读预览，注册不会提交。请勿输入真实账号或密码。
      </p>
      <a-form :model="formState" name="register" layout="vertical" @finish="handleSubmit">
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
            placeholder="至少6位，UTF-8编码不超过72字节"
            autocomplete="new-password"
          />
        </a-form-item>
        <a-form-item
          label="确认密码"
          name="checkPassword"
          :rules="[{ required: true, message: '请确认密码!' }]"
          ><a-input-password
            v-model:value="formState.checkPassword"
            placeholder="请确认密码"
            autocomplete="new-password"
        /></a-form-item>
        <a-form-item
          ><a-button
            type="primary"
            html-type="submit"
            class="auth-submit"
            :loading="submitting"
            :disabled="designPreview"
            aria-label="注册"
            >注册</a-button
          ></a-form-item
        >
      </a-form>
      <p class="auth-switch">已有账号？<RouterLink to="/user/login">去登录</RouterLink></p>
    </div>
  </AuthShell>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import AuthShell from '@/components/AuthShell.vue'
import { userRegisterUsingPost } from '@/service/api/userController'

const router = useRouter()

const designPreview = import.meta.env.DEV && import.meta.env.MODE === 'design'
const submitting = ref(false)
const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})
async function handleSubmit(values: API.UserRegisterRequest) {
  if (designPreview || submitting.value) return
  if (!values.userPassword || values.userPassword.length < 6 || new TextEncoder().encode(values.userPassword).length > 72) {
    message.error('密码至少6位，UTF-8编码不超过72字节')
    return
  }
  if (values.userPassword !== values.checkPassword) {
    message.error('两次输入的密码不一致')
    return
  }
  submitting.value = true
  try {
    const res = await userRegisterUsingPost(values)
    if (res.code !== 0) throw new Error(res.message || '注册失败')
    message.success('注册成功，请登录')
    await router.push('/user/login')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '注册失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>
