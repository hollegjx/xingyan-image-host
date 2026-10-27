import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SearchPicturePage from '@/pages/SearchPicturePage.vue'
import { getPictureVoByIdUsingGet } from '@/service/api/pictureController'
import { searchPictureByPictureUsingPost } from '@/service/api/pictureSearchController'

vi.mock('@/service/api/pictureController', () => ({ getPictureVoByIdUsingGet: vi.fn() }))
vi.mock('@/service/api/pictureSearchController', () => ({
  searchPictureByPictureUsingPost: vi.fn(),
}))

const picture = (id: string) => ({ id, name: `图片 ${id}`, uploadStatus: 1, reviewStatus: 1 })
const stubs = {
  'a-spin': { template: '<span>loading</span>' },
  'a-alert': {
    props: ['message', 'description'],
    template: '<div>{{ message }} {{ description }}<slot name="action" /></div>',
  },
  'a-button': {
    props: ['disabled', 'loading'],
    template: '<button :disabled="disabled || loading"><slot /></button>',
  },
  'a-checkbox': {
    props: ['checked'],
    emits: ['update:checked'],
    template:
      '<label><input type="checkbox" :checked="checked" @change="$emit(\'update:checked\', $event.target.checked)"/><slot /></label>',
  },
  'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
}

async function openPage(id: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/picture/search', component: SearchPicturePage },
      { path: '/picture/:id', component: { template: '<div />' } },
    ],
  })
  await router.push({ path: '/picture/search', query: { pictureId: id } })
  await router.isReady()
  const wrapper = mount(SearchPicturePage, { global: { plugins: [router], stubs } })
  await flushPromises()
  return { router, wrapper }
}

describe('search picture async boundaries', () => {
  beforeEach(() => vi.resetAllMocks())

  it('does not let a stale metadata response overwrite a new source', async () => {
    let resolveOld!: (value: API.BaseResponsePictureVO_) => void
    vi.mocked(getPictureVoByIdUsingGet).mockImplementation(({ id }) =>
      String(id) === '1'
        ? new Promise((resolve) => {
            resolveOld = resolve
          })
        : Promise.resolve({ code: 0, data: picture(String(id)) }),
    )
    const { router, wrapper } = await openPage('1')
    await router.push('/picture/search?pictureId=2')
    await flushPromises()
    resolveOld({ code: 0, data: picture('1') })
    await flushPromises()
    expect(wrapper.text()).toContain('图片 2')
    expect(wrapper.text()).not.toContain('图片 1')
    expect(searchPictureByPictureUsingPost).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('changing source aborts the old request and resets consent and results', async () => {
    vi.mocked(getPictureVoByIdUsingGet).mockImplementation(({ id }) =>
      Promise.resolve({ code: 0, data: picture(String(id)) }),
    )
    let resolveSearch!: (value: API.BaseResponseListImageSearchResult_) => void
    vi.mocked(searchPictureByPictureUsingPost).mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveSearch = resolve
        }),
    )
    const { router, wrapper } = await openPage('1')
    await wrapper.get('input[type=checkbox]').setValue(true)
    await wrapper.get('button').trigger('click')
    expect(searchPictureByPictureUsingPost).toHaveBeenCalledTimes(1)
    const options = vi.mocked(searchPictureByPictureUsingPost).mock.calls[0]![1]
    await router.push('/picture/search?pictureId=2')
    await flushPromises()
    expect(options?.signal.aborted).toBe(true)
    resolveSearch({
      code: 0,
      data: [{ thumbUrl: 'https://images.example/old.png', fromUrl: 'https://example.com/old' }],
    })
    await flushPromises()
    expect(wrapper.find('.results').exists()).toBe(false)
    expect((wrapper.get('input').element as HTMLInputElement).checked).toBe(false)
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('图片 2')
    wrapper.unmount()
  })
})
