import { parseId, sameId } from './space'

export function normalizePictureColor(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const color = value.trim()
  if (/^#[\da-f]{6}$/i.test(color)) return color.toLowerCase()
  if (/^0x[\da-f]{1,6}$/i.test(color)) return `#${color.slice(2).toLowerCase().padStart(6, '0')}`
  return undefined
}

/** Share the app route, never COS URLs, current query strings, session tokens or signed URLs. */
export function pictureShareLink(id: unknown, origin: string, base = '/'): string {
  const pictureId = parseId(id)
  if (!pictureId) throw new Error('图片 ID 无效')
  const site = new URL(origin)
  if (!['https:', 'http:'].includes(site.protocol)) throw new Error('分享地址无效')
  if (!base.startsWith('/') || base.startsWith('//') || /[?#\\]/.test(base))
    throw new Error('网站基础路径无效')
  return new URL(`${base.replace(/\/$/, '')}/picture/${pictureId}`, site.origin).href
}

export function renamePreview(rule: string, count: number): string[] {
  const value = rule.trim()
  if (!value || value.length > 128) throw new Error('命名规则需为 1 至 128 个字符')
  const literal = value.split('{序号}').join('')
  if (
    /[{}]/.test(literal) ||
    [...value].some((char) => char.charCodeAt(0) < 32 || char.charCodeAt(0) === 127)
  )
    throw new Error('仅支持 {序号} 占位符，不支持表达式')
  return Array.from({ length: count }, (_, index) => {
    const name = value.split('{序号}').join(String(index + 1))
    if (name.length > 128) throw new Error('生成的图片名称超过 128 个字符')
    return name
  })
}

export interface BatchPictureValues {
  changeCategory: boolean
  category: string
  changeTags: boolean
  tags: string[]
  rename: boolean
  nameRule: string
}

export function buildBatchPictureRequest(
  spaceId: unknown,
  pictures: API.PictureVO[],
  values: BatchPictureValues,
): API.PictureEditByBatchRequest {
  const space = parseId(spaceId)
  if (!space || !pictures.length || pictures.length > 100)
    throw new Error('请选择当前空间的 1 至 100 张图片')
  const ids = pictures.map((picture) => {
    const id = parseId(picture.id)
    if (!id || !sameId(picture.spaceId, space)) throw new Error('所选图片不属于当前空间')
    return id
  })
  if (new Set(ids).size !== ids.length) throw new Error('不能重复选择图片')
  const body: API.PictureEditByBatchRequest = { spaceId: space, pictureIdList: ids }
  if (values.changeCategory) {
    body.category = values.category.trim()
    if (body.category.length > 64) throw new Error('分类不能超过 64 个字符')
  }
  if (values.changeTags) {
    const tags = values.tags.map((tag) => tag.trim())
    if (tags.length > 20 || tags.some((tag) => !tag || tag.length > 32))
      throw new Error('最多 20 个标签，每个 1 至 32 个字符')
    body.tags = [...new Set(tags)]
    if (JSON.stringify(body.tags).length > 512) throw new Error('标签总长度过长')
  }
  if (values.rename) {
    renamePreview(values.nameRule, ids.length)
    body.nameRule = values.nameRule.trim()
  }
  if (!values.changeCategory && !values.changeTags && !values.rename)
    throw new Error('请至少选择一项修改内容')
  return body
}
