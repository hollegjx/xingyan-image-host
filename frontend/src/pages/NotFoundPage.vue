<template>
  <section class="not-found">
    <BrandMark class="lost-mark" />
    <span class="eyebrow">星檐 · 404</span>
    <h1>这片屋檐下，暂时没有这一页</h1>
    <p>地址可能写错了，也可能已经更换。回到图库，继续看看吧。</p>
    <router-link to="/" class="return-home">返回首页 <ArrowRightOutlined /></router-link>
  </section>
</template>
<script setup lang="ts">
import BrandMark from '@/components/BrandMark.vue'
import { ArrowRightOutlined } from '@ant-design/icons-vue'
</script>
<style scoped>
.not-found {
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 16px;
}
.lost-mark {
  width: 56px;
  height: 56px;
  margin-bottom: 28px;
}
.not-found h1 {
  font-size: clamp(24px, 3vw, 36px);
  margin: 18px 0 12px;
  font-weight: 600;
}
.not-found p {
  font-size: 13px;
  color: var(--muted);
  margin: 0 0 30px;
}
.return-home {
  display: inline-flex;
  align-items: center;
  gap: 20px;
  padding: 12px 24px;
  border-radius: 100px;
  background: var(--ink);
  color: white;
}
</style>
