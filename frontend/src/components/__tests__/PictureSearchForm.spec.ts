import { flushPromises, mount } from '@vue/test-utils'
import Antd, { DatePicker } from 'ant-design-vue'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import PictureSearchForm from '../PictureSearchForm.vue'

describe('PictureSearchForm', () => {
  beforeAll(() => {
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  })
  afterAll(() => vi.unstubAllGlobals())

  it('a real form submission emits a filter snapshot', async () => {
    const wrapper = mount(PictureSearchForm, { global: { plugins: [Antd] } })
    await wrapper.get('input[placeholder="搜索空间内图片"]').setValue(' 日落 ')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.emitted('search')).toEqual([[{ searchText: '日落' }]])
    await wrapper.get('input[placeholder="搜索空间内图片"]').setValue('夜景')
    expect(wrapper.emitted('search')?.[0]).toEqual([{ searchText: '日落' }])
    wrapper.unmount()
  })

  it('reset clears fields and immediately requests an unfiltered page', async () => {
    const wrapper = mount(PictureSearchForm, { global: { plugins: [Antd] } })
    await wrapper.get('input[placeholder="搜索空间内图片"]').setValue('日落')
    const reset = wrapper
      .findAll('button')
      .find((button) => button.text().replace(/\s/g, '') === '重置')!
    await reset.trigger('click')
    expect(wrapper.emitted('search')).toEqual([[{}]])
    expect(
      (wrapper.get('input[placeholder="搜索空间内图片"]').element as HTMLInputElement).value,
    ).toBe('')
    wrapper.unmount()
  })

  it('date selection is converted before submit and cleared by reset', async () => {
    const wrapper = mount(PictureSearchForm, { global: { plugins: [Antd] } })
    await wrapper.get('.ant-collapse-header').trigger('click')
    wrapper
      .findComponent(DatePicker.RangePicker)
      .vm.$emit('update:value', ['2026-10-01', '2026-10-02'])
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.emitted('search')?.[0]).toEqual([
      {
        startEditTime: '2026-09-30T16:00:00.000Z',
        endEditTime: '2026-10-02T16:00:00.000Z',
      },
    ])
    await wrapper
      .findAll('button')
      .find((button) => button.text().replace(/\s/g, '') === '重置')!
      .trigger('click')
    expect(wrapper.findComponent(DatePicker.RangePicker).props('value')).toBeUndefined()
    wrapper.unmount()
  })

  it('compact mode reveals filters on demand and keeps their values when collapsed', async () => {
    const wrapper = mount(PictureSearchForm, {
      props: { compact: true },
      attachTo: document.body,
      global: { plugins: [Antd] },
    })
    expect(wrapper.get('.filter-panel').isVisible()).toBe(false)
    await wrapper.get('button[aria-label="筛选"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('.filter-panel').isVisible()).toBe(true)
    await wrapper.get('#picture-search_category').setValue('自然')
    await wrapper.get('button[aria-label="筛选"]').trigger('click')
    await wrapper.get('input[placeholder="搜索空间内图片"]').setValue('山')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.emitted('search')?.[0]).toEqual([{ searchText: '山', category: '自然' }])
    wrapper.unmount()
  })
})
