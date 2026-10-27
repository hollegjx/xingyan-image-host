import { describe, expect, it } from 'vitest'
import {
  buildBatchPictureRequest,
  normalizePictureColor,
  pictureShareLink,
  renamePreview,
  type BatchPictureValues,
} from '../pictureExtensions'

const values = (): BatchPictureValues => ({
  changeCategory: false,
  category: '',
  changeTags: false,
  tags: [],
  rename: false,
  nameRule: '',
})
const pictures = [
  { id: '9007199254740997', spaceId: '101', name: 'a' },
  { id: '11', spaceId: '101', name: 'b' },
]

describe('picture extensions', () => {
  it('normalizes canonical and legacy colors without inventing a color', () => {
    expect(normalizePictureColor('0x080e0')).toBe('#0080e0')
    expect(normalizePictureColor('#AaBbCc')).toBe('#aabbcc')
    for (const color of ['', null, 'red', '#fff', '0x1234567', 'url(x)', 123])
      expect(normalizePictureColor(color)).toBeUndefined()
  })
  it('shares only the app route including base path and exact Long ID', () => {
    expect(
      pictureShareLink(
        '9007199254740997',
        'https://example.com:8443/secret?token=abc',
        '/gallery/',
      ),
    ).toBe('https://example.com:8443/gallery/picture/9007199254740997')
    expect(pictureShareLink('11', 'http://localhost:5173')).toBe('http://localhost:5173/picture/11')
    expect(() => pictureShareLink('javascript:alert(1)', 'https://example.com')).toThrow()
    expect(() => pictureShareLink(9007199254740997, 'https://example.com')).toThrow()
    expect(() => pictureShareLink('1', 'https://example.com', '//evil.com')).toThrow()
  })
  it('previews literal replacements and rejects unknown expressions and excessive final length', () => {
    expect(renamePreview('旅行_{序号}', 2)).toEqual(['旅行_1', '旅行_2'])
    expect(renamePreview('$ {序号} {序号}', 2)).toEqual(['$ 1 1', '$ 2 2'])
    expect(renamePreview('统一名称', 2)).toEqual(['统一名称', '统一名称'])
    for (const rule of ['', '{unknown}', '#{T(x)}', 'a\nb', 'a'.repeat(129)])
      expect(() => renamePreview(rule, 2)).toThrow()
    expect(() => renamePreview('a'.repeat(126) + '{序号}', 100)).toThrow()
  })
  it('retains selection order and distinguishes clear from unchanged', () => {
    const body = buildBatchPictureRequest('101', pictures, {
      ...values(),
      changeTags: true,
      changeCategory: true,
      rename: true,
      nameRule: '图片_{序号}',
    })
    expect(body).toEqual({
      spaceId: '101',
      pictureIdList: ['9007199254740997', '11'],
      category: '',
      tags: [],
      nameRule: '图片_{序号}',
    })
    const renameOnly = buildBatchPictureRequest('101', pictures, {
      ...values(),
      rename: true,
      nameRule: '图片_{序号}',
    })
    expect(renameOnly).not.toHaveProperty('category')
    expect(renameOnly).not.toHaveProperty('tags')
  })
  it('rejects empty, duplicate, cross-space, oversized or invalid selections', () => {
    const changes = { ...values(), rename: true, nameRule: 'x' }
    for (const rows of [
      [],
      [pictures[0]!, pictures[0]!],
      [{ id: '1', spaceId: '102' }],
      [{ id: '1' }],
      Array.from({ length: 101 }, () => pictures[0]!),
    ]) {
      expect(() => buildBatchPictureRequest('101', rows, changes)).toThrow()
    }
    expect(() => buildBatchPictureRequest('101', pictures, values())).toThrow()
  })
  it('validates category and tags before sending', () => {
    expect(() =>
      buildBatchPictureRequest('101', pictures, {
        ...values(),
        changeCategory: true,
        category: 'x'.repeat(65),
      }),
    ).toThrow()
    expect(() =>
      buildBatchPictureRequest('101', pictures, { ...values(), changeTags: true, tags: [' '] }),
    ).toThrow()
    const body = buildBatchPictureRequest('101', pictures, {
      ...values(),
      changeTags: true,
      tags: ['海边', ' 海边 '],
    })
    expect(body.tags).toEqual(['海边'])
  })
})
