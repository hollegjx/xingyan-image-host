import { describe, expect, it } from 'vitest'
import { canSearchPublicPicture, safeImageSearchResults, safeImageSearchUrl } from '../imageSearch'

describe('external image search boundaries', () => {
  it('only offers completed approved public records', () => {
    const picture = { id: '9007199254740999', uploadStatus: 1, reviewStatus: 1 }
    expect(canSearchPublicPicture(picture)).toBe(true)
    expect(canSearchPublicPicture({ ...picture, spaceId: '3' })).toBe(false)
    expect(canSearchPublicPicture({ ...picture, uploadStatus: 0 })).toBe(false)
    expect(canSearchPublicPicture({ ...picture, reviewStatus: 0 })).toBe(false)
    expect(canSearchPublicPicture({ ...picture, id: Number(picture.id) })).toBe(false)
  })
  it.each([
    'javascript:alert(1)',
    'data:image/png;base64,AA',
    'http://example.com/a',
    'https://user:pass@example.com/a',
    'https://127.0.0.1/a',
    'https://[::1]/a',
    'https://localhost/a',
    'https://server.local/a',
    'https://example.com:8123/a',
    'https://example.com/a#x',
    'https://example.com\\@evil.example/a',
  ])('rejects unsafe URL %s', (url) => {
    expect(safeImageSearchUrl(url)).toBeUndefined()
  })
  it('deduplicates and caps safe results', () => {
    const rows = Array.from({ length: 80 }, (_, i) => ({
      thumbUrl: `https://images.example/${Math.floor(i / 2)}.png`,
      fromUrl: 'https://example.com/source',
    }))
    expect(safeImageSearchResults(rows)).toHaveLength(30)
    expect(
      safeImageSearchResults([{ thumbUrl: 'javascript:x', fromUrl: 'https://example.com' }]),
    ).toEqual([])
  })
})
