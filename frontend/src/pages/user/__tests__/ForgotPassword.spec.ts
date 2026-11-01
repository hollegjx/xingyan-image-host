import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { expect, it, vi } from 'vitest'
import UserLoginPage from '../UserLoginPage.vue'
vi.mock('vue-router', () => ({ useRouter: () => ({}), useRoute: () => ({ query: {} }) }))
vi.mock('@/service/api/userController', () => ({ userLoginUsingPost: vi.fn(), getLoginUserUsingGet: vi.fn() }))
it('opens administrator recovery guidance without requesting email recovery', async () => {
  const wrapper = mount(UserLoginPage, { global: { plugins: [createPinia()], stubs: {
    AuthShell: { template: '<div><slot /></div>' },
    'a-button': { template: '<button><slot /></button>' },
    'a-modal': { props: ['open'], template: '<div v-if="open"><slot /></div>' },
    RouterLink: true,
  } } })
  const help = wrapper.findAll('button').find((b) => b.text() === '忘记密码？')!
  await help.trigger('click'); expect(wrapper.text()).toContain('请联系管理员重置')
  expect(wrapper.text()).toContain('到设置里修改密码'); expect(wrapper.text()).toContain('暂不提供邮件找回'); wrapper.unmount()
})

it('shows configured recovery contact as plain text', async () => {
  vi.stubEnv('VITE_PASSWORD_RESET_CONTACT', '请通过站点帮助入口联系管理员')
  try {
    const wrapper = mount(UserLoginPage, { global: { plugins: [createPinia()], stubs: {
      AuthShell: { template: '<div><slot /></div>' },
      'a-button': { template: '<button><slot /></button>' },
      'a-modal': { props: ['open'], template: '<div v-if="open"><slot /></div>' },
      RouterLink: true,
    } } })
    await wrapper.findAll('button').find(b => b.text() === '忘记密码？')!.trigger('click')
    expect(wrapper.text()).toContain('请通过站点帮助入口联系管理员')
    wrapper.unmount()
  } finally { vi.unstubAllEnvs() }
})
