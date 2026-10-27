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
    expect(wrapper.text()).toContain('私人图片链接不会授予访问权限')
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
})
