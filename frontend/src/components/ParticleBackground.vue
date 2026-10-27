<template>
  <canvas v-show="enabled" ref="canvas" class="particle-background" aria-hidden="true" />
</template>

<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import {
  advanceParticles,
  createParticles,
  type AmbientParticle,
  type Point,
} from '@/utils/ambientParticles'

const props = withDefaults(defineProps<{ enabled?: boolean; paused?: boolean }>(), {
  enabled: true,
  paused: false,
})
const emit = defineEmits<{ reducedMotion: [value: boolean] }>()
const canvas = ref<HTMLCanvasElement>()
let context: CanvasRenderingContext2D | null = null
let particles: AmbientParticle[] = []
let pointer: Point | null = null
let width = 0,
  height = 0,
  lastFrame = 0
let frame: number | null = null
let motionQuery: MediaQueryList | undefined
let coarseQuery: MediaQueryList | undefined
const colors = ['100, 119, 144', '91, 138, 198', '86, 153, 151']

function canAnimate() {
  return props.enabled && !props.paused && !motionQuery?.matches && !document.hidden
}

function draw() {
  if (!context) return
  const ctx = context
  ctx.clearRect(0, 0, width, height)
  if (!props.enabled) return
  const points = particles.map((particle) => ({
    x: particle.x + particle.offsetX,
    y: particle.y + particle.offsetY,
  }))
  // At most 80 particles; no unbounded spawning or DOM nodes per particle.
  for (let i = 0; i < points.length; i++) {
    const a = points[i]!
    for (let j = i + 1; j < points.length; j++) {
      const b = points[j]!
      const distance = Math.hypot(a.x - b.x, a.y - b.y)
      if (distance >= 155) continue
      ctx.strokeStyle = `rgba(112, 138, 169, ${0.18 * (1 - distance / 155)})`
      ctx.lineWidth = 0.8
      ctx.beginPath()
      ctx.moveTo(a.x, a.y)
      ctx.lineTo(b.x, b.y)
      ctx.stroke()
    }
    const particle = particles[i]!
    const nearby = pointer ? Math.max(0, 1 - Math.hypot(a.x - pointer.x, a.y - pointer.y) / 150) : 0
    ctx.fillStyle = `rgba(${colors[particle.tint]}, ${0.4 + nearby * 0.25})`
    ctx.beginPath()
    ctx.arc(a.x, a.y, particle.radius + nearby * 0.5, 0, Math.PI * 2)
    ctx.fill()
  }
}

function stop() {
  if (frame !== null) cancelAnimationFrame(frame)
  frame = null
  lastFrame = 0
}

function tick(now: number) {
  frame = null
  if (!canAnimate()) return
  if (!lastFrame) lastFrame = now
  const elapsed = now - lastFrame
  // Background animation is intentionally capped at 30fps.
  if (elapsed >= 1000 / 30) {
    advanceParticles(particles, elapsed / 1000, width, height, pointer)
    draw()
    lastFrame = now
  }
  frame = requestAnimationFrame(tick)
}

function synchronize() {
  stop()
  pointer = null
  for (const particle of particles) particle.offsetX = particle.offsetY = 0
  emit('reducedMotion', motionQuery?.matches ?? false)
  draw()
  if (context && canAnimate()) frame = requestAnimationFrame(tick)
}

function resize() {
  if (!canvas.value || !context) return
  width = window.innerWidth
  height = window.innerHeight
  const ratio = Math.min(window.devicePixelRatio || 1, 2)
  canvas.value.width = Math.round(width * ratio)
  canvas.value.height = Math.round(height * ratio)
  context.setTransform(ratio, 0, 0, ratio, 0, 0)
  particles = createParticles(width, height, coarseQuery?.matches ?? false)
  synchronize()
}

function onPointerMove(event: PointerEvent) {
  if (!canAnimate() || coarseQuery?.matches || event.pointerType !== 'mouse') return
  pointer = { x: event.clientX, y: event.clientY }
}
function clearPointer() {
  pointer = null
}

watch(() => [props.enabled, props.paused], synchronize)
onMounted(() => {
  context = canvas.value?.getContext('2d') ?? null
  if (!context) return
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  coarseQuery = window.matchMedia('(pointer: coarse)')
  motionQuery.addEventListener('change', synchronize)
  coarseQuery.addEventListener('change', resize)
  window.addEventListener('resize', resize, { passive: true })
  window.addEventListener('pointermove', onPointerMove, { passive: true })
  window.addEventListener('blur', clearPointer)
  window.addEventListener('scroll', clearPointer, { passive: true })
  document.addEventListener('pointerleave', clearPointer)
  document.addEventListener('pointercancel', clearPointer)
  document.addEventListener('visibilitychange', synchronize)
  resize()
})
onBeforeUnmount(() => {
  stop()
  motionQuery?.removeEventListener('change', synchronize)
  coarseQuery?.removeEventListener('change', resize)
  window.removeEventListener('resize', resize)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('blur', clearPointer)
  window.removeEventListener('scroll', clearPointer)
  document.removeEventListener('pointerleave', clearPointer)
  document.removeEventListener('pointercancel', clearPointer)
  document.removeEventListener('visibilitychange', synchronize)
  particles = []
  context = null
})
</script>

<style scoped>
.particle-background {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  user-select: none;
  z-index: 0;
}
@media print {
  .particle-background {
    display: none;
  }
}
</style>
