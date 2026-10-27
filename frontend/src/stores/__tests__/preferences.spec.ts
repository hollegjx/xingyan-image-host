import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { MOTION_KEY, PARTICLES_KEY, usePreferencesStore } from '../usePreferencesStore'

describe('browser preferences', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })
  afterEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
  })
  it('keeps the existing background setting and persists changes without account data', () => {
    localStorage.setItem(PARTICLES_KEY, 'off')
    const store = usePreferencesStore()
    expect(store.particles).toBe(false)
    store.setParticles(true)
    store.setReduceMotion(true)
    expect(localStorage.getItem(PARTICLES_KEY)).toBe('on')
    expect(localStorage.getItem(MOTION_KEY)).toBe('on')
    setActivePinia(createPinia())
    expect(usePreferencesStore().reduceMotion).toBe(true)
    expect(localStorage.length).toBe(2)
  })
  it('still applies the session choice if storage is unavailable', () => {
    vi.spyOn(localStorage, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const store = usePreferencesStore()
    store.setParticles(false)
    expect(store.particles).toBe(false)
    expect(store.persistent).toBe(false)
  })
  it('survives read failures', () => {
    vi.spyOn(localStorage, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const store = usePreferencesStore()
    expect(store.particles).toBe(true)
    expect(store.persistent).toBe(false)
  })
  it('syncs other tabs and resets only visual preferences', () => {
    localStorage.setItem('unrelated', 'keep')
    const store = usePreferencesStore()
    localStorage.setItem(MOTION_KEY, 'on')
    store.sync(new StorageEvent('storage', { key: MOTION_KEY }))
    expect(store.reduceMotion).toBe(true)
    store.reset()
    expect(store.particles).toBe(true)
    expect(store.reduceMotion).toBe(false)
    expect(localStorage.getItem('unrelated')).toBe('keep')
  })
})
