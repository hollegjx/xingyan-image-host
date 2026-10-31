import { beforeEach, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import Antd, { message, Upload } from 'ant-design-vue'
import PictureUpload from '../PictureUpload.vue'

vi.mock('@/service/api/pictureController', () => ({
  uploadPictureUsingPost: vi.fn(), uploadPictureByUrlUsingPost: vi.fn(),
}))
beforeEach(() => { vi.restoreAllMocks() })
it('rejects every file in a multi-selection with one clear notice', () => {
  const warning = vi.spyOn(message, 'warning').mockImplementation(() => (() => {}) as ReturnType<typeof message.warning>)
  const wrapper = mount(PictureUpload, { global: { plugins: [Antd] } })
  const beforeUpload = wrapper.findComponent(Upload.Dragger).props('beforeUpload')!
  const files = [new File(['a'], 'a.png', { type: 'image/png' }), new File(['b'], 'b.png', { type: 'image/png' })]
  for (const file of files) expect(beforeUpload(file as never, files as never)).toBe(Upload.LIST_IGNORE)
  expect(warning).toHaveBeenCalledExactlyOnceWith('一次只能上传一张，本次选择了 2 个文件，均未上传，请重新选择')
  wrapper.unmount()
})
