<template>
  <section class="account-page">
    <header class="account-heading">
      <span class="eyebrow">你的账户</span>
      <h1>{{ title }}<span>。</span></h1>
      <p>{{ description }}</p>
    </header>
    <nav class="account-navigation" aria-label="账户导航">
      <router-link
        to="/user/profile"
        :aria-current="route.name === 'user-profile' ? 'page' : undefined"
        ><UserOutlined />个人资料</router-link
      >
      <router-link
        to="/user/settings"
        :aria-current="route.name === 'user-settings' ? 'page' : undefined"
        ><SettingOutlined />偏好与设置</router-link
      >
    </nav>
    <slot />
  </section>
</template>
<script setup lang="ts">
import { useRoute } from 'vue-router'
import { SettingOutlined, UserOutlined } from '@ant-design/icons-vue'
defineProps<{ title: string; description: string }>()
const route = useRoute()
</script>
<style scoped>
.account-page {
  max-width: 980px;
  margin: 0 auto;
  padding: 24px 0 20px;
}
.account-heading .eyebrow {
  color: var(--muted);
}
.account-heading h1 {
  margin: 14px 0 12px;
  font-size: clamp(32px, 4vw, 42px);
  font-weight: 650;
}
.account-heading h1 > span {
  color: #b7b7bf;
}
.account-heading p {
  margin: 0;
  font-size: 14px;
  color: var(--muted);
}
.account-navigation {
  display: flex;
  gap: 30px;
  border-bottom: 1px solid var(--line);
  margin: 34px 0 30px;
}
.account-navigation a {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--muted);
  padding: 0 2px 16px;
  border-bottom: 2px solid transparent;
  font-size: 13px;
}
.account-navigation a[aria-current] {
  color: var(--ink);
  border-color: var(--ink);
}
.account-page :deep(.account-panel) {
  border: 1px solid var(--line);
  border-radius: 20px;
  background: #fff;
  padding: 30px;
  margin-bottom: 22px;
}
.account-page :deep(.account-panel h2) {
  font-size: 17px;
  margin: 0;
  font-weight: 600;
}
.account-page :deep(.account-note) {
  font-size: 12px;
  color: var(--muted);
  line-height: 1.8;
}
.account-page :deep(.account-panel-heading) {
  display: flex;
  gap: 16px;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
}
@media (max-width: 560px) {
  .account-page {
    padding-top: 8px;
  }
  .account-page :deep(.account-panel) {
    padding: 22px 20px;
    border-radius: 16px;
  }
  .account-navigation {
    margin-top: 28px;
  }
}
</style>
