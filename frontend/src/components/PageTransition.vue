<template>
  <div
    ref="stage"
    class="route-stage"
    :style="heldHeight ? { minHeight: `${heldHeight}px` } : undefined"
  >
    <Transition
      name="workspace-page"
      :mode="reducedMotion ? undefined : 'out-in'"
      :css="!reducedMotion"
      @before-leave="beforeLeave"
      @after-enter="afterEnter"
      @enter-cancelled="releaseHeight"
      @leave-cancelled="cancelLeave"
    >
      <div :key="viewKey" class="route-page" :data-view="viewKey"><slot /></div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'

defineProps<{ viewKey: string; reducedMotion: boolean }>()
const stage = ref<HTMLElement>()
const heldHeight = ref(0)
let moveFocus = false

function beforeLeave(element: Element) {
  // Keep the footer in place while out-in temporarily removes the old view.
  heldHeight.value = Math.ceil(stage.value?.getBoundingClientRect().height || 0)
  moveFocus = element.contains(document.activeElement)
  // A departing form must not remain clickable or keyboard-focusable during its fade.
  element.setAttribute('inert', '')
  element.setAttribute('aria-hidden', 'true')
}
function releaseHeight() {
  heldHeight.value = 0
}
function afterEnter(element: Element) {
  releaseHeight()
  if (moveFocus) {
    const target = element.querySelector<HTMLElement>('h1, h2')
    if (target) {
      target.setAttribute('tabindex', '-1')
      target.focus({ preventScroll: true })
    }
  }
  moveFocus = false
}
function cancelLeave(element: Element) {
  element.removeAttribute('inert')
  element.removeAttribute('aria-hidden')
  moveFocus = false
  releaseHeight()
}
onBeforeUnmount(releaseHeight)
</script>

<style scoped>
.route-stage,
.route-page {
  display: flow-root;
  width: 100%;
  min-width: 0;
}
.workspace-page-enter-active {
  transition:
    opacity 240ms ease,
    transform 240ms cubic-bezier(0.22, 1, 0.36, 1);
}
.workspace-page-leave-active {
  transition:
    opacity 100ms ease,
    transform 100ms ease;
  pointer-events: none;
}
.workspace-page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.workspace-page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
/* Stable pages have no transform/filter: fixed overlays and colorful images stay untouched. */
:global(html[data-reduce-motion='true']) .route-page {
  transition: none !important;
  transform: none !important;
}
@media (prefers-reduced-motion: reduce) {
  .route-page {
    transition: none !important;
    transform: none !important;
  }
}
</style>
