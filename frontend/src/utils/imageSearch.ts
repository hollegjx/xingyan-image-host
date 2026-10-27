import { parseId } from '@/utils/space'

export function canSearchPublicPicture(picture: API.PictureVO): boolean {
  return (
    !!parseId(picture.id) &&
    picture.spaceId == null &&
    picture.uploadStatus === 1 &&
    picture.reviewStatus === 1
  )
}

/** Third-party links are untrusted even after server-side sanitization. */
export function safeImageSearchUrl(value: unknown): string | undefined {
  if (typeof value !== 'string' || value.length > 4096 || value.includes('\\')) return undefined
  try {
    const url = new URL(value)
    const host = url.hostname.toLowerCase()
    if (url.protocol !== 'https:' || url.username || url.password || url.hash || url.port)
      return undefined
    if (
      !/^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?$/.test(host) ||
      !host.includes('.') ||
      /^[0-9.]+$/.test(host) ||
      /\.(localhost|local|internal)$/.test(host)
    )
      return undefined
    return url.href
  } catch {
    return undefined
  }
}

export function safeImageSearchResults(rows: API.ImageSearchResult[]): API.ImageSearchResult[] {
  const seen = new Set<string>()
  const result: API.ImageSearchResult[] = []
  for (const row of rows) {
    const thumbUrl = safeImageSearchUrl(row.thumbUrl)
    const fromUrl = safeImageSearchUrl(row.fromUrl)
    const key = `${thumbUrl}\n${fromUrl}`
    if (thumbUrl && fromUrl && !seen.has(key)) {
      seen.add(key)
      result.push({ thumbUrl, fromUrl })
      if (result.length === 30) break
    }
  }
  return result
}
