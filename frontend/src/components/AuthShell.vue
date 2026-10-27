<template>
  <section class="auth-shell">
    <div class="auth-story">
      <span class="eyebrow">星檐图床 · 收好每一帧日常</span>
      <EaveMotif class="auth-eave" />
      <h2>把画面收好，<br />让日常有光。</h2>
      <p>像屋檐收住一场雨，<br />这里，安放你想留下的每一张图片。</p>
      <img src="@/assets/gallery-art.svg" alt="" /><span class="story-foot"
        >XINGYAN · A HOME FOR IMAGES</span
      >
    </div>
    <div class="auth-panel"><slot /></div>
  </section>
</template>
<script setup lang="ts">
import EaveMotif from '@/components/EaveMotif.vue'
</script>
<style scoped>
.auth-eave {
  margin-top: 22px;
  width: 90px;
}
.auth-shell {
  display: grid;
  grid-template-columns: 1fr 1fr;
  border: 1px solid var(--line);
  border-radius: 24px;
  overflow: hidden;
  max-width: 960px;
  margin: 36px auto;
  background: white;
  min-height: 560px;
}
.auth-story {
  position: relative;
  padding: 50px 40px 35px;
  background: var(--surface-soft);
  display: flex;
  flex-direction: column;
}
.auth-story .eyebrow {
  font-size: 11px;
  color: var(--muted);
}
.auth-story h2 {
  font-size: 36px;
  line-height: 1.35;
  margin: 15px 0 17px;
  font-weight: 600;
}
.auth-story p {
  color: var(--muted);
  font-size: 13px;
}
.auth-story img {
  width: 100%;
  max-width: 330px;
  align-self: center;
  margin-top: auto;
}
.story-foot {
  font-size: 8px;
  letter-spacing: 0.18em;
  color: var(--muted);
  margin-top: 15px;
}
.auth-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 50px;
  min-width: 0;
}
.auth-panel :deep(h1) {
  font-size: 29px;
  font-weight: 650;
  margin: 0 0 12px;
  letter-spacing: -0.04em;
}
.auth-panel :deep(.auth-subtitle) {
  color: var(--muted);
  font-size: 12px;
  margin: 0 0 30px;
}
.auth-panel :deep(.ant-form-item-label > label) {
  font-size: 12px;
  color: var(--ink);
}
.auth-panel :deep(.ant-input),
.auth-panel :deep(.ant-input-affix-wrapper) {
  min-height: 42px;
}
.auth-panel :deep(.ant-input-affix-wrapper .ant-input) {
  min-height: auto;
}
.auth-panel :deep(.auth-submit) {
  height: 46px;
  border-radius: 100px;
  width: 100%;
  margin-top: 8px;
}
.auth-panel :deep(.auth-switch) {
  font-size: 12px;
  color: var(--muted);
  text-align: center;
  margin: 18px 0 0;
}
.auth-panel :deep(.auth-switch a) {
  color: var(--ink);
  text-decoration: underline;
  text-underline-offset: 4px;
  margin-left: 5px;
}
.auth-panel :deep(.preview-warning) {
  font-size: 12px;
  color: #515154;
  background: var(--surface-soft);
  border-radius: 7px;
  padding: 11px;
  margin: 0 0 20px;
  line-height: 1.7;
}
@media (max-width: 1100px) {
  .auth-panel {
    padding: 36px 30px;
  }
  .auth-story {
    padding: 38px 28px;
  }
}
@media (max-width: 650px) {
  .auth-shell {
    display: block;
    min-height: 0;
    margin: 8px auto;
    max-width: 440px;
  }
  .auth-story {
    display: none;
  }
  .auth-panel {
    padding: 40px 26px;
  }
}
</style>
