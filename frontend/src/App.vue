<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import { ref } from 'vue'
import { ConfigProvider } from 'ant-design-vue'
import router from '@/router'
import zhCN from 'ant-design-vue/es/locale/zh_CN'

// 静态 Modal.confirm 在独立应用中渲染，也需要全局按钮配置。
const staticUiConfig = { prefixCls: 'ant', autoInsertSpaceInButton: false }
ConfigProvider.config(staticUiConfig)
const initialRouteReady = ref(false)
void router.isReady().then(() => { initialRouteReady.value = true })
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
        colorTextSecondary: '#636366',
        colorBorder: '#dedee3',
        colorBgLayout: '#ffffff',
        borderRadius: 10,
        controlHeight: 40,
        fontSize: 14,
        fontFamily:
          '-apple-system, BlinkMacSystemFont, Segoe UI, PingFang SC, Microsoft YaHei, sans-serif',
      },
      components: {
        // colorInfo 是墨色，信息提示默认会变成深灰底；这里改回浅灰底的安静样式
        Alert: { colorInfo: '#6e6e73', colorInfoBg: '#f5f5f7', colorInfoBorder: '#e8e8ed' },
      },
    }"
  >
    <div class="app">
      <BasicLayout />
      <div v-if="!initialRouteReady" class="initial-skeleton" role="status" aria-label="正在加载页面" aria-busy="true">
        <a-skeleton active :paragraph="{ rows: 8 }" />
      </div>
    </div>
  </a-config-provider>
</template>

<style scoped>
.initial-skeleton { position: absolute; top: 120px; left: calc(var(--sidebar-width) + 42px); right: 42px; min-height: 360px; }
@media (max-width: 800px) { .initial-skeleton { top: 100px; left: 20px; right: 20px; } }
</style>
