import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import PictureShareModal from '../PictureShareModal.vue'

const stubs = {
  'a-modal': { template: '<div><slot /></div>' },
  'a-alert': {
    props: ['message', 'description'],
    template: '<div>{{ message }} {{ description }}</div>',
  },
  'a-input': { props: ['value'], template: '<input :value="value" />' },
  'a-button': { template: '<button><slot /></button>' },
  'a-qrcode': { props: ['value'], template: '<div data-qr>{{ value }}</div>' },
  'a-segmented': { template: '<div />' },
}
afterEach(() => vi.unstubAllGlobals())
describe('picture sharing', () => {
  it('shares an application URL and warns that private access is not granted', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { clipboard: { writeText } })
    const wrapper = mount(PictureShareModal, {
      props: {
        open: true,
        picture: {
          id: '9007199254740997',
          spaceId: '7',
          url: 'https://private.example/secret?sign=xyz',
        },
      },
      global: { stubs },
    })
    expect(wrapper.text()).toContain('空间里的图片没有公开直链')
    expect(wrapper.text()).not.toContain('sign=xyz')
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(writeText).toHaveBeenCalledWith(expect.stringContaining('/picture/9007199254740997'))
    expect(wrapper.text()).toContain('链接已复制')
  })
  it('offers manual copy if the clipboard is unavailable', async () => {
    vi.stubGlobal('navigator', {})
    const wrapper = mount(PictureShareModal, {
      props: { open: true, picture: { id: '1', uploadStatus: 1, reviewStatus: 1 } },
      global: { stubs },
    })
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('自动复制失败')
  })
  it('offers a direct link only for published public pictures', () => {
    const url = 'https://cdn.example/public/a.webp'
    const published = mount(PictureShareModal, {
      props: {
        open: true,
        picture: { id: '1', name: '海', url, uploadStatus: 1, reviewStatus: 1 },
      },
      global: { stubs },
    })
    expect(published.get('#picture-embed-code').element).toHaveProperty('value', url)
    const pending = mount(PictureShareModal, {
      props: { open: true, picture: { id: '2', url, uploadStatus: 1, reviewStatus: 0 } },
      global: { stubs },
    })
    expect(pending.find('#picture-embed-code').exists()).toBe(false)
    const privatePicture = mount(PictureShareModal, {
      props: {
        open: true,
        picture: { id: '3', spaceId: '7', url, uploadStatus: 1, reviewStatus: 1 },
      },
      global: { stubs },
    })
    expect(privatePicture.find('#picture-embed-code').exists()).toBe(false)
  })
})
