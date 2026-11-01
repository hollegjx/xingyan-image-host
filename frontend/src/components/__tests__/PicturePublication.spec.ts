import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest'
import Share from '../PictureShareModal.vue'
import Submit from '../PictureSubmitModal.vue'
const api = vi.hoisted(() => ({ submit: vi.fn(), link: vi.fn(), taxonomy: vi.fn() }))
vi.mock('@/service/api/pictureController', () => ({ listPictureTagCategoryUsingGet: api.taxonomy }))
vi.mock('@/service/api/picturePublicationController', () => ({
  submitPublicPicture: api.submit,
  generatePrivatePictureLink: api.link,
}))
const stubs = {
  'a-modal': { template: '<div><slot /></div>' },
  'a-alert': {
    props: ['message', 'description'],
    template: '<p>{{ message }} {{ description }}</p>',
  },
  'a-button': { template: '<button><slot /></button>' },
  'a-input': { props: ['value'], template: '<input :value="value" />' },
  'a-auto-complete': {
    props: ['value'],
    emits: ['update:value'],
    template: `<input :value="value" @input="$emit('update:value', $event.target.value)" />`,
  },
  'a-qrcode': true,
  'a-segmented': true,
  RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
}
const picture = { id: '9007199254740997', spaceId: '10', uploadStatus: 1 }
const globals = { stubs }
beforeEach(() => {
  vi.resetAllMocks()
  api.taxonomy.mockResolvedValue({ code: 0, data: { categoryList: ['风景'] } })
})
afterEach(() => vi.unstubAllGlobals())
const button = (wrapper: ReturnType<typeof mount>, text: string) =>
  wrapper.findAll('button').find((b) => b.text() === text)!
describe('private publication', () => {
  it('requires an explicit confirmation before submission and provides the submissions link', async () => {
    api.submit.mockResolvedValue({ code: 0, data: '123' })
    const w = mount(Submit, { props: { open: true, picture }, global: globals })
    expect(w.text()).toContain('审核通过后所有人可见')
    expect(api.submit).not.toHaveBeenCalled()
    await button(w, '确认投稿').trigger('click')
    await flushPromises()
    expect(api.submit).toHaveBeenCalledWith(picture.id, '')
    expect(w.text()).toContain('已提交，等待审核')
    expect(w.get('a').attributes('href')).toBe('/picture/mine')
  })
  it('defaults category from the source, submits edited category and permits cancel', async () => {
    api.submit.mockResolvedValue({ code: 0, data: '123' })
    const w = mount(Submit, {
      props: { open: true, picture: { ...picture, category: '原分类' } },
      global: globals,
    })
    expect((w.get('#submission-category').element as HTMLInputElement).value).toBe('原分类')
    await w.get('#submission-category').setValue('风景')
    await button(w, '取消').trigger('click')
    expect(w.emitted('update:open')).toEqual([[false]])
    expect(api.submit).not.toHaveBeenCalled()
    await button(w, '确认投稿').trigger('click')
    await flushPromises()
    expect(api.submit).toHaveBeenCalledWith(picture.id, '风景')
    expect(w.emitted('submitted')).toEqual([['123']])
  })
  it('shows rejection without inventing a new successful submission', async () => {
    api.submit.mockResolvedValue({ code: 50001, message: '已有待审核的投稿' })
    const w = mount(Submit, { props: { open: true, picture }, global: globals })
    await button(w, '确认投稿').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('已有待审核的投稿')
    expect(w.text()).not.toContain('已提交，等待审核')
  })
  it('ignores a previous image submission response', async () => {
    let resolve!: (v: unknown) => void
    api.submit.mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    const w = mount(Submit, { props: { open: true, picture }, global: globals })
    await button(w, '确认投稿').trigger('click')
    await w.setProps({ picture: { ...picture, id: '12' } })
    resolve({ code: 0, data: '123' })
    await flushPromises()
    expect(w.text()).not.toContain('已提交，等待审核')
  })
  it('does not offer temporary links to unauthorized viewers or public pictures', () => {
    const cases: { open: boolean; picture: API.PictureVO; canGeneratePrivateLink?: boolean }[] = [
      { open: true, picture },
      { open: true, picture: { id: '1', uploadStatus: 1 }, canGeneratePrivateLink: true },
    ]
    for (const props of cases) {
      const w = mount(Share, { props, global: globals })
      expect(w.find('#private-link-hours').exists()).toBe(false)
    }
  })
  it.each([1, 24, 168])(
    'signs only after warning and explicit generation for %s hours',
    async (hours) => {
      api.link.mockResolvedValue({
        code: 0,
        data: {
          url: 'https://example.invalid/temporary-display',
          expiresAt: '2030-01-01T00:00:00Z',
        },
      })
      const writeText = vi.fn().mockResolvedValue(undefined)
      vi.stubGlobal('navigator', { clipboard: { writeText } })
      const w = mount(Share, {
        props: { open: true, picture, canGeneratePrivateLink: true },
        global: globals,
      })
      expect(w.text()).toContain('拿到链接的人在有效期内都能查看')
      expect(api.link).not.toHaveBeenCalled()
      await w.get('#private-link-hours').setValue(String(hours))
      await button(w, '生成临时直链').trigger('click')
      await flushPromises()
      expect(api.link).toHaveBeenCalledWith(picture.id, hours)
      await button(w, '复制临时直链').trigger('click')
      await flushPromises()
      expect(writeText).toHaveBeenCalledWith('https://example.invalid/temporary-display')
      await button(w, '复制 Markdown').trigger('click')
      await flushPromises()
      expect(writeText).toHaveBeenLastCalledWith(
        '![图片](https://example.invalid/temporary-display)',
      )
      expect(w.text()).toMatch(/到期时间：\d{4}-\d{2}-\d{2} \d{2}:\d{2}/)
      await w.setProps({ open: false })
      expect(w.find('#private-temporary-link').exists()).toBe(false)
    },
  )
  it('discards delayed links after switching images or losing permission', async () => {
    let resolve!: (v: unknown) => void
    api.link.mockReturnValue(
      new Promise((r) => {
        resolve = r
      }),
    )
    const w = mount(Share, {
      props: { open: true, picture, canGeneratePrivateLink: true },
      global: globals,
    })
    await button(w, '生成临时直链').trigger('click')
    await w.setProps({ picture: { ...picture, id: '12' }, canGeneratePrivateLink: false })
    resolve({
      code: 0,
      data: { url: 'https://example.invalid/temporary-display', expiresAt: '2030-01-01' },
    })
    await flushPromises()
    expect(w.find('#private-temporary-link').exists()).toBe(false)
  })
  it('clears previous credentials on lifetime change and shows a safe error on network failure', async () => {
    api.link.mockRejectedValue(new Error('network'))
    const w = mount(Share, {
      props: { open: true, picture, canGeneratePrivateLink: true },
      global: globals,
    })
    await button(w, '生成临时直链').trigger('click')
    await flushPromises()
    expect(w.text()).toContain('生成失败，请重试')
    expect(w.find('#private-temporary-link').exists()).toBe(false)
  })
})
