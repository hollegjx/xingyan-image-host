import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, expect, it, vi } from 'vitest'
import GalleryCard from '../GalleryCard.vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
const api = vi.hoisted(() => ({ set: vi.fn(), push: vi.fn(), success: vi.fn(), error: vi.fn() }))
vi.mock('@/service/api/pictureFavoriteController', () => ({ setPictureFavorite: api.set }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }), useRoute: () => ({ fullPath: '/?category=test' }) }))
vi.mock('ant-design-vue', () => ({ message: { success: api.success, error: api.error } }))
const mountCard = () => mount(GalleryCard, { props: { picture: { id: '10', name: '测试图片' }, favoriteStatus: { favoriteCount: 2, favorited: false } }, global: { stubs: { RouterLink: { template: '<a><slot /></a>' }, PictureThumbnail: true, PictureSearchButton: true } } })
beforeEach(() => { setActivePinia(createPinia()); vi.resetAllMocks() })
it('anonymous favorite does not mutate and preserves the return route', async () => {
  const wrapper = mountCard()
  await wrapper.get('.favorite-button').trigger('click')
  expect(api.set).not.toHaveBeenCalled()
  expect(api.push).toHaveBeenCalledWith({ path: '/user/login', query: { redirect: '/?category=test' } })
  wrapper.unmount()
})
it('deduplicates pending writes and shows the confirmed count and feedback', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  let resolve!: (value: unknown) => void
  api.set.mockReturnValue(new Promise(r => { resolve = r }))
  const wrapper = mountCard()
  await wrapper.get('.favorite-button').trigger('click')
  await wrapper.get('.favorite-button').trigger('click')
  expect(api.set).toHaveBeenCalledExactlyOnceWith('10', true)
  resolve({ code: 0, data: { favoriteCount: 3, favorited: true } })
  await flushPromises()
  expect(wrapper.get('.favorite-button').text()).toContain('3')
  expect(api.success).toHaveBeenCalledWith('已收藏')
  wrapper.unmount()
})
it('failed writes preserve the count and explain the failure', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  api.set.mockResolvedValue({ code: 50000, message: '暂时不可用' })
  const wrapper = mountCard()
  await wrapper.get('.favorite-button').trigger('click'); await flushPromises()
  expect(wrapper.get('.favorite-button').text()).toContain('2')
  expect(wrapper.get('.favorite-button').attributes('aria-pressed')).toBe('false')
  expect(api.error).toHaveBeenCalledWith('暂时不可用')
  wrapper.unmount()
})
it('ignores a late response after the user changes', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  let resolve!: (value: unknown) => void
  api.set.mockReturnValue(new Promise(r => { resolve = r }))
  const wrapper = mountCard()
  await wrapper.get('.favorite-button').trigger('click')
  useLoginUserStore().clearLoginUser(); await flushPromises()
  resolve({ code: 0, data: { favoriteCount: 3, favorited: true } }); await flushPromises()
  expect(api.success).not.toHaveBeenCalled()
  expect(wrapper.get('.favorite-button').attributes('aria-pressed')).toBe('false')
  wrapper.unmount()
})
