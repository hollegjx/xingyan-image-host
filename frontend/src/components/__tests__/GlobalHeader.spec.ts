import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import Antd from 'ant-design-vue'
import GlobalHeader from '../GlobalHeader.vue'

describe('login link deployment base', () => {
  beforeAll(() => vi.stubGlobal('matchMedia', (media: string) => ({
    matches: false, media, onchange: null,
    addListener: vi.fn(), removeListener: vi.fn(),
    addEventListener: vi.fn(), removeEventListener: vi.fn(), dispatchEvent: vi.fn(),
  })))
  afterAll(() => vi.unstubAllGlobals())
  it.each(['/', '/tuku/'])('keeps login within %s', async (base) => {
    const router = createRouter({
      history: createMemoryHistory(base),
      routes: [{ path: '/', component: { template: '<div />' } },
        { path: '/user/login', component: { template: '<div />' } }],
    })
    await router.push('/')
    await router.isReady()
    const wrapper = mount(GlobalHeader, { global: { plugins: [createPinia(), router, Antd] } })
    expect(wrapper.find(`a[href="${base}user/login"]`).exists()).toBe(true)
    wrapper.unmount()
  })
})
