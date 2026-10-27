import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import ParticleBackground from '../ParticleBackground.vue'

describe('ParticleBackground lifecycle', () => {
  const frames = new Map<number, FrameRequestCallback>()
  const queries = new Map<string, { matches: boolean; listeners: Set<() => void> }>()
  let wrapper: VueWrapper | undefined
  let sequence = 0
  const context = {
    clearRect: vi.fn(),
    setTransform: vi.fn(),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    stroke: vi.fn(),
    arc: vi.fn(),
    fill: vi.fn(),
  }
  beforeEach(() => {
    frames.clear()
    queries.clear()
    vi.stubGlobal(
      'requestAnimationFrame',
      vi.fn((callback: FrameRequestCallback) => {
        frames.set(++sequence, callback)
        return sequence
      }),
    )
    vi.stubGlobal(
      'cancelAnimationFrame',
      vi.fn((id: number) => frames.delete(id)),
    )
    vi.stubGlobal(
      'matchMedia',
      vi.fn((query: string) => {
        const state = { matches: false, listeners: new Set<() => void>() }
        queries.set(query, state)
        return {
          get matches() {
            return state.matches
          },
          addEventListener: (_event: string, callback: () => void) => state.listeners.add(callback),
          removeEventListener: (_event: string, callback: () => void) =>
            state.listeners.delete(callback),
        }
      }),
    )
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(
      context as unknown as CanvasRenderingContext2D,
    )
  })
  afterEach(() => {
    wrapper?.unmount()
    wrapper = undefined
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    vi.clearAllMocks()
  })

  it('draws a persistent field with one scheduled loop and cancels on unmount', () => {
    wrapper = mount(ParticleBackground)
    expect(context.arc).toHaveBeenCalled()
    expect(frames.size).toBe(1)
    wrapper.unmount()
    wrapper = undefined
    expect(frames.size).toBe(0)
    for (const query of queries.values()) expect(query.listeners.size).toBe(0)
  })

  it('clears and stops when switched off, and starts only one loop on enable', async () => {
    wrapper = mount(ParticleBackground)
    await wrapper.setProps({ enabled: false })
    expect(frames.size).toBe(0)
    expect(wrapper.get('canvas').attributes('style')).toContain('display: none')
    await wrapper.setProps({ enabled: true })
    expect(frames.size).toBe(1)
  })

  it('responds to a live reduced-motion change with a static field', () => {
    wrapper = mount(ParticleBackground)
    const motion = queries.get('(prefers-reduced-motion: reduce)')!
    motion.matches = true
    for (const listener of motion.listeners) listener()
    expect(frames.size).toBe(0)
    expect(wrapper.emitted('reducedMotion')?.at(-1)).toEqual([true])
    motion.matches = false
    for (const listener of motion.listeners) listener()
    expect(frames.size).toBe(1)
  })

  it('pauses when the document is hidden and resumes when visible', () => {
    wrapper = mount(ParticleBackground)
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    expect(frames.size).toBe(0)
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    expect(frames.size).toBe(1)
  })

  it('degrades safely when canvas is unavailable', () => {
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null)
    wrapper = mount(ParticleBackground)
    expect(frames.size).toBe(0)
    expect(queries.size).toBe(0)
  })

  it('pauses for the explicit preference without hiding the static background', async () => {
    wrapper = mount(ParticleBackground)
    await wrapper.setProps({ paused: true })
    expect(frames.size).toBe(0)
    expect(wrapper.get('canvas').isVisible()).toBe(true)
    await wrapper.setProps({ paused: false })
    expect(frames.size).toBe(1)
  })
})
