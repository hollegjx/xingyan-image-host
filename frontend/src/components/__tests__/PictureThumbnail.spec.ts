import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import PictureThumbnail from '../PictureThumbnail.vue'

const picture = { id: 1, name: '测试图片', url: '/full.webp', thumbnailUrl: '/thumb.webp' }
const mountImage = (value = picture, preview = false) =>
  mount(PictureThumbnail, {
    props: { picture: value, preview },
    global: { stubs: { AImage: true } },
  })

describe('PictureThumbnail', () => {
  it('uses a lazy thumbnail in a stable frame without mounting the full-size preview', () => {
    const wrapper = mountImage(picture, true)
    expect(wrapper.get('img').attributes()).toMatchObject({
      src: '/thumb.webp', loading: 'lazy', decoding: 'async', alt: '测试图片',
    })
    expect(wrapper.attributes('style')).toContain('height: 180px')
    expect(wrapper.findComponent({ name: 'AImage' }).exists()).toBe(false)
  })

  it.each([undefined, null, '', '   '])('falls back for a missing/blank thumbnail: %s', (thumbnailUrl) => {
    const wrapper = mount(PictureThumbnail, {
      props: { picture: { url: picture.url, thumbnailUrl: thumbnailUrl as string | undefined } },
    })
    expect(wrapper.get('img').attributes('src')).toBe('/full.webp')
  })

  it('falls back once on error and stops requesting when both sources fail', async () => {
    const wrapper = mountImage()
    await wrapper.get('img').trigger('error')
    expect(wrapper.get('img').attributes('src')).toBe('/full.webp')
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('[role="img"]').text()).toBe('图片暂不可用')
  })

  it('does not retry an identical thumbnail and display URL', async () => {
    const wrapper = mountImage({ ...picture, thumbnailUrl: picture.url })
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('resets failed sources when reused for another picture', async () => {
    const wrapper = mountImage()
    await wrapper.get('img').trigger('error')
    await wrapper.setProps({ picture: { ...picture, id: 2 } })
    expect(wrapper.get('img').attributes('src')).toBe('/thumb.webp')
  })

  it('mounts the main image only after preview is requested', async () => {
    const wrapper = mountImage(picture, true)
    await wrapper.get('button').trigger('click')
    expect(wrapper.get('a-image-stub').attributes('src')).toBe('/full.webp')
    await wrapper.setProps({ picture: { ...picture, url: '/replacement.webp' } })
    expect(wrapper.find('a-image-stub').exists()).toBe(false)
  })
})
