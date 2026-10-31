import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import request from '@/request'
import { useLoginUserStore } from '@/stores/useLoginUserStore'

const mocks = vi.hoisted(() => ({
  warning: vi.fn(), replace: vi.fn(),
  route: { path: '/picture/10', fullPath: '/picture/10?from=gallery#info', hash: '#info' },
}))
vi.mock('ant-design-vue', () => ({ message: { warning: mocks.warning } }))
vi.mock('@/router', () => ({ default: { currentRoute: { value: mocks.route }, replace: mocks.replace } }))

function respond(url = '/api/picture/favorite/set', code = 40100, rejected = false) {
  return request(url, { adapter: async (config) => {
    const response = { data: { code }, status: rejected ? 401 : 200, statusText: '', headers: {}, config }
    if (rejected) throw Object.assign(new Error('Unauthorized'), { response, config })
    return response
  } })
}
beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  Object.assign(mocks.route, { path: '/picture/10', fullPath: '/picture/10?from=gallery#info', hash: '#info' })
})
it('clears an expired session and redirects only once for concurrent failures', async () => {
  const store = useLoginUserStore()
  store.setLoginUser({ id: '1', userName: 'reader' })
  await Promise.all([respond(), respond()])
  expect(store.loginUser).toEqual({})
  expect(mocks.warning).toHaveBeenCalledExactlyOnceWith('登录已过期，请重新登录')
  expect(mocks.replace).toHaveBeenCalledExactlyOnceWith({ path: '/user/login', query: { redirect: '/picture/10?from=gallery#info' }, hash: '' })
})
it('does not prompt or redirect anonymous public visitors', async () => {
  await respond()
  expect(mocks.warning).not.toHaveBeenCalled()
  expect(mocks.replace).not.toHaveBeenCalled()
})
it('does not intercept the login lookup even with an existing local user', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  await respond('/api/user/get/login?refresh=1')
  expect(mocks.warning).not.toHaveBeenCalled()
  expect(mocks.replace).not.toHaveBeenCalled()
})
it('preserves successful responses and unrelated errors', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  await expect(respond('/api/picture/get/vo', 0)).resolves.toEqual({ code: 0 })
  await respond('/api/picture/get/vo', 50000)
  expect(useLoginUserStore().loginUser.id).toBe('1')
  expect(mocks.warning).not.toHaveBeenCalled()
})
it('also handles a 40100 body in a rejected HTTP response', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  await expect(respond('/api/picture/favorite/set', 40100, true)).rejects.toThrow('Unauthorized')
  expect(useLoginUserStore().loginUser).toEqual({})
  expect(mocks.replace).toHaveBeenCalledTimes(1)
})
it('keeps invitation fragments out of the redirect query', async () => {
  Object.assign(mocks.route, { path: '/space/invite', fullPath: '/space/invite#test-fragment', hash: '#test-fragment' })
  useLoginUserStore().setLoginUser({ id: '1' })
  await respond()
  expect(mocks.replace).toHaveBeenCalledWith({ path: '/user/login', query: { redirect: '/space/invite' }, hash: '#test-fragment' })
})
it('does not replace the existing return destination on the login page', async () => {
  mocks.route.path = '/user/login'
  useLoginUserStore().setLoginUser({ id: '1' })
  await respond()
  expect(useLoginUserStore().loginUser).toEqual({})
  expect(mocks.replace).not.toHaveBeenCalled()
})
