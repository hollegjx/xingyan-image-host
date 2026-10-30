import { shallowMount, flushPromises } from '@vue/test-utils'
import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useLoginUserStore } from '@/stores/useLoginUserStore'
import PictureDetailPage from '../../PictureDetailPage.vue'
import MyFavoritesPage from '../MyFavoritesPage.vue'
const api = vi.hoisted(() => ({
  detail: vi.fn(),
  set: vi.fn(),
  list: vi.fn(),
  push: vi.fn(),
  error: vi.fn(),
}))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: api.push }) }))
vi.mock('@/service/api/pictureController', () => ({
  getPictureVoByIdUsingGet: api.detail,
  deletePictureUsingPost: vi.fn(),
}))
vi.mock('@/service/api/pictureFavoriteController', () => ({
  setPictureFavorite: api.set,
  listMyFavorites: api.list,
}))
vi.mock('@/service/api/spaceController', () => ({
  getSpaceVoUsingGet: vi
    .fn()
    .mockResolvedValue({ code: 0, data: { id: '99', permissionList: ['picture:view'] } }),
}))
vi.mock('ant-design-vue', () => ({ message: { error: api.error }, Modal: { confirm: vi.fn() } }))
type Detail = {
  toggleFavorite: () => Promise<void>
  picture: API.PictureDetailVO
  canFavorite: boolean
}
type Mine = {
  remove: (p: API.PictureVO) => Promise<void>
  changePage: (p: number) => void
  pictures: API.PictureVO[]
  error: string
  current: number
}
beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
  api.detail.mockResolvedValue({
    code: 0,
    data: { id: '10', uploadStatus: 1, reviewStatus: 1, favoriteCount: 4, favorited: false },
  })
  api.set.mockResolvedValue({ code: 0, data: { favoriteCount: 5, favorited: true } })
  api.list.mockResolvedValue({
    code: 0,
    data: { records: [{ id: '10', name: '收藏作品' }], total: 25 },
  })
})
it('anonymous favorite click leads to login without sending a mutation', async () => {
  const wrapper = shallowMount(PictureDetailPage, { props: { id: '10' } })
  await flushPromises()
  await (wrapper.vm as unknown as Detail).toggleFavorite()
  expect(api.set).not.toHaveBeenCalled()
  expect(api.push).toHaveBeenCalledWith({ path: '/user/login', query: { redirect: '/picture/10' } })
  wrapper.unmount()
})
it('favorite and unfavorite update only from successful server results', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  const wrapper = shallowMount(PictureDetailPage, { props: { id: '10' } })
  await flushPromises()
  const vm = wrapper.vm as unknown as Detail
  await vm.toggleFavorite()
  expect(api.set).toHaveBeenLastCalledWith('10', true)
  expect(vm.picture.favoriteCount).toBe(5)
  expect(vm.picture.favorited).toBe(true)
  api.set.mockResolvedValue({ code: 0, data: { favoriteCount: 4, favorited: false } })
  await vm.toggleFavorite()
  expect(api.set).toHaveBeenLastCalledWith('10', false)
  expect(vm.picture.favoriteCount).toBe(4)
  expect(vm.picture.favorited).toBe(false)
  wrapper.unmount()
})
it('failed favorite preserves count and displays a retryable error', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  api.set.mockResolvedValue({ code: 40400, message: '图片不存在或不可收藏' })
  const wrapper = shallowMount(PictureDetailPage, { props: { id: '10' } })
  await flushPromises()
  const vm = wrapper.vm as unknown as Detail
  await vm.toggleFavorite()
  expect(vm.picture.favoriteCount).toBe(4)
  expect(vm.picture.favorited).toBe(false)
  expect(api.error).toHaveBeenCalled()
  wrapper.unmount()
})
it('draft and private details cannot send a favorite request', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  for (const change of [{ uploadStatus: 0 }, { reviewStatus: 0 }, { spaceId: '99' }]) {
    api.detail.mockResolvedValue({
      code: 0,
      data: { id: '10', uploadStatus: 1, reviewStatus: 1, ...change },
    })
    const wrapper = shallowMount(PictureDetailPage, { props: { id: '10' } })
    await flushPromises()
    const vm = wrapper.vm as unknown as Detail
    expect(vm.canFavorite).toBe(false)
    await vm.toggleFavorite()
    wrapper.unmount()
  }
  expect(api.set).not.toHaveBeenCalled()
})
it('late favorite response cannot alter another picture or duplicate a pending click', async () => {
  useLoginUserStore().setLoginUser({ id: '1' })
  let finish!: (value: unknown) => void
  api.set.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  const wrapper = shallowMount(PictureDetailPage, { props: { id: '10' } })
  await flushPromises()
  const vm = wrapper.vm as unknown as Detail
  const pending = vm.toggleFavorite()
  await vm.toggleFavorite()
  expect(api.set).toHaveBeenCalledTimes(1)
  api.detail.mockResolvedValue({
    code: 0,
    data: { id: '11', favoriteCount: 2, uploadStatus: 1, reviewStatus: 1 },
  })
  await wrapper.setProps({ id: '11' })
  await flushPromises()
  finish({ code: 0, data: { favoriteCount: 5, favorited: true } })
  await pending
  expect(vm.picture.id).toBe('11')
  expect(vm.picture.favoriteCount).toBe(2)
  wrapper.unmount()
})
it('my favorites sends pagination and removes only the selected record', async () => {
  const wrapper = shallowMount(MyFavoritesPage)
  await flushPromises()
  const vm = wrapper.vm as unknown as Mine
  expect(api.list).toHaveBeenCalledWith(1, 12)
  vm.changePage(2)
  await flushPromises()
  expect(api.list).toHaveBeenLastCalledWith(2, 12)
  await vm.remove({ id: '10' })
  expect(api.set).toHaveBeenCalledWith('10', false)
  expect(api.list).toHaveBeenCalledTimes(3)
  wrapper.unmount()
})
it('failed list clears previous records and shows error instead of empty success', async () => {
  const wrapper = shallowMount(MyFavoritesPage)
  await flushPromises()
  const vm = wrapper.vm as unknown as Mine
  api.list.mockResolvedValue({ code: 50001, message: '加载失败' })
  vm.changePage(2)
  await flushPromises()
  expect(vm.pictures).toEqual([])
  expect(vm.error).toBe('加载失败')
  wrapper.unmount()
})
it('deleting the final record of the last page returns to a valid page', async () => {
  const wrapper = shallowMount(MyFavoritesPage)
  await flushPromises()
  const vm = wrapper.vm as unknown as Mine
  vm.changePage(3)
  await flushPromises()
  api.list.mockResolvedValue({ code: 0, data: { records: [], total: 24 } })
  await vm.remove({ id: '10' })
  expect(vm.current).toBe(2)
  expect(api.list).toHaveBeenLastCalledWith(2, 12)
  wrapper.unmount()
})
