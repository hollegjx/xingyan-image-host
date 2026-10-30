<template>
  <header class="page-header">
    <div class="page-header-text">
      <button v-if="back" type="button" class="page-back" @click="emit('back')">
        <LeftOutlined />{{ back }}
      </button>
      <span v-if="eyebrow" class="eyebrow">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header-actions"><slot name="actions" /></div>
  </header>
</template>

<script setup lang="ts">
import { LeftOutlined } from '@ant-design/icons-vue'

// 工作台页面统一的标题区：小标题、大标题与说明，与「我的投稿」「团队空间」一致
defineProps<{ title: string; eyebrow?: string; description?: string; back?: string }>()
const emit = defineEmits<{ back: [] }>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
  margin: 32px 0 28px;
}
.page-header-text {
  min-width: 0;
}
.page-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 18px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--muted);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.page-back:hover {
  color: var(--ink);
}
.page-header .eyebrow {
  display: block;
  color: var(--muted);
  letter-spacing: 0.15em;
}
.page-header h1 {
  margin: 12px 0 10px;
  font-size: clamp(30px, 3.4vw, 42px);
  font-weight: 650;
  line-height: 1.2;
  overflow-wrap: anywhere;
}
.page-header p {
  max-width: 680px;
  margin: 0;
  color: var(--muted);
  font-size: 14px;
  line-height: 1.7;
}
.page-header-actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
    align-items: flex-start;
    margin-top: 20px;
  }
  .page-header h1 {
    font-size: 28px;
  }
}
</style>
