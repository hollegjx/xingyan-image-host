import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import Antd from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
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
    const pinia = createPinia()
    const wrapper = mount(GlobalHeader, { global: { plugins: [pinia, router, Antd] } })
    expect(wrapper.find('a.upload-link').exists()).toBe(false)
    expect(wrapper.find('[aria-label="用户菜单"]').exists()).toBe(false)
    useLoginUserStore(pinia).clearLoginUser()
    await wrapper.vm.$nextTick()
    expect(wrapper.find(`a[href="${base}user/login"]`).exists()).toBe(true)
    wrapper.unmount()
  })
})
