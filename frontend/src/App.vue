<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

const loginUserStore = useLoginUserStore()
// 页面加载时获取当前登录用户信息
loginUserStore.fetchLoginUser()
</script>

<template>
  <a-config-provider
    :locale="zhCN"
    :auto-insert-space-in-button="false"
    :theme="{
      token: {
        colorPrimary: '#1d1d1f',
        colorInfo: '#1d1d1f',
        colorText: '#1d1d1f',
        colorTextSecondary: '#6e6e73',
        colorBorder: '#dedee3',
        colorBgLayout: '#ffffff',
        borderRadius: 10,
        controlHeight: 40,
        fontSize: 14,
        fontFamily:
          '-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif',
      },
    }"
  >
    <div class="app">
      <BasicLayout />
    </div>
  </a-config-provider>
</template>

<style scoped></style>
