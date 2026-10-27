<script setup lang="ts">
import GlobalHeader from '@/components/GlobalHeader.vue'
import ParticleBackground from '@/components/ParticleBackground.vue'
import PageTransition from '@/components/PageTransition.vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { usePreferencesStore } from '@/stores/usePreferencesStore'
import { routeViewKey } from '@/utils/galleryNavigation'
const designPreview = import.meta.env.DEV && import.meta.env.MODE === 'design'
const preferences = usePreferencesStore()
const backgroundEnabled = computed(() => preferences.particles)
const systemReducedMotion = ref(false)
const reducedMotion = computed(() => systemReducedMotion.value || preferences.reduceMotion)
function toggleBackground() {
  preferences.setParticles(!preferences.particles)
}
watch(
  () => preferences.reduceMotion,
  (value) => {
    document.documentElement.dataset.reduceMotion = String(value)
  },
  { immediate: true },
)
onMounted(() => window.addEventListener('storage', preferences.sync))
onBeforeUnmount(() => {
  window.removeEventListener('storage', preferences.sync)
  delete document.documentElement.dataset.reduceMotion
})
</script>

<template>
  <div class="basicLayout">
    <ParticleBackground
      :enabled="backgroundEnabled"
      :paused="preferences.reduceMotion"
      @reduced-motion="systemReducedMotion = $event"
    />
    <GlobalHeader />
    <div class="workspace-body">
      <div v-if="designPreview" class="design-notice" role="status">
        <span class="preview-dot" /> 本地设计预览
        <span>· 示例数据 · 所有写入已禁用，不连接线上服务</span>
      </div>
      <main id="main-content" class="workspace-content">
        <router-view v-slot="{ Component, route: viewRoute }">
          <!-- Name identifies the page, not a filter/page/ID change inside the same page. -->
          <PageTransition
            v-if="Component"
            :view-key="routeViewKey(viewRoute)"
            :reduced-motion="reducedMotion"
          >
            <component :is="Component" />
          </PageTransition>
        </router-view>
      </main>
      <footer class="workspace-footer footer">
        <span>星檐图床 <span class="footer-dot">/</span> 檐下有图，心中有光。</span>
        <div class="footer-actions">
          <button
            type="button"
            class="background-toggle"
            :aria-pressed="backgroundEnabled"
            title="开启时仍遵循系统的减少动态效果设置"
            @click="toggleBackground"
          >
            背景{{ reducedMotion ? '粒子' : '动效' }}：{{
              backgroundEnabled ? (reducedMotion ? '静态' : '开') : '关'
            }}
          </button>
          <a href="https://github.com/hollegjx" target="_blank" rel="noopener noreferrer"
            >Made by GJX ↗</a
          >
        </div>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.basicLayout {
  isolation: isolate;
}
.workspace-body {
  position: relative;
  z-index: 1;
  margin-left: var(--sidebar-width);
  padding-top: 78px;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.workspace-content {
  width: 100%;
  max-width: 1680px;
  margin: 0 auto;
  padding: 38px 42px 48px;
  flex: 1;
  min-width: 0;
}
.workspace-footer {
  margin: 0 42px;
  padding: 22px 0;
  border-top: 1px solid var(--line);
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 11px;
  color: var(--muted);
}
.workspace-footer a {
  color: var(--muted);
}
.footer-actions {
  display: flex;
  align-items: center;
  gap: 20px;
}
.background-toggle {
  padding: 2px 0;
  border: 0;
  background: transparent;
  color: var(--muted);
  font: inherit;
  cursor: pointer;
}
.background-toggle:hover {
  color: var(--ink);
}
.footer-dot {
  margin: 0 12px;
  color: #d2d2d7;
}
.design-notice {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 16px;
  background: var(--surface-soft);
  color: var(--ink);
  font-size: 11px;
  border-bottom: 1px solid var(--line);
}
.design-notice > span:not(.preview-dot) {
  color: var(--muted);
}
.preview-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--ink);
}
@media (min-width: 1700px) {
  .workspace-content {
    padding-left: 64px;
    padding-right: 64px;
  }
}
@media (max-width: 1100px) {
  .workspace-content {
    padding: 28px;
  }
}
@media (max-width: 800px) {
  .workspace-body {
    margin-left: 0;
    padding-top: 68px;
  }
  .workspace-content {
    padding: 24px 20px 36px;
  }
  .workspace-footer {
    margin: 0 20px;
    font-size: 10px;
  }
}
</style>
