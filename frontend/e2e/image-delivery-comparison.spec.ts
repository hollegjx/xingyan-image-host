import { test, expect } from '@playwright/test'
import { readFileSync } from 'node:fs'

const image = readFileSync(new URL('../src/assets/logo.svg', import.meta.url))

// Controlled request-count comparison, NOT a COS compression or latency benchmark.
// Identical fixture bytes isolate eager versus lazy delivery; no invented compression ratio.
test('compare eager main-image loading with current lazy thumbnails', async ({ browser }, testInfo) => {
  const results: object[] = []
  for (const width of [390, 1280]) {
    for (let run = 1; run <= 3; run++) {
      const counts: number[] = []
      for (const mode of ['baseline', 'optimized']) {
        const context = await browser.newContext({ viewport: { width, height: 800 } })
        try {
          const page = await context.newPage()
          const requested = new Set<string>()
          const pictures = Array.from({ length: 12 }, (_, i) => ({
            id: i + 1,
            name: `图片 ${i + 1}`,
            url: `/comparison/main-${i}.svg`,
            thumbnailUrl: mode === 'optimized' ? `/comparison/thumb-${i}.svg` : undefined,
          }))
          await page.route('**/api/**', (route) => {
            const path = new URL(route.request().url()).pathname
            const data = path === '/api/picture/tag_category'
              ? { categoryList: [], tagList: [] }
              : path === '/api/user/get/login'
                ? { id: 1, userName: '测试用户', userRole: 0 }
                : { records: pictures, total: pictures.length }
            return route.fulfill({ json: { code: 0, data } })
          })
          await page.route('**/comparison/**', (route) => {
            requested.add(new URL(route.request().url()).pathname)
            return route.fulfill({ contentType: 'image/svg+xml', body: image })
          })
          await page.goto('/')
          const covers = page.locator('#homePage .picture-thumbnail img')
          await expect(covers).toHaveCount(12)
          if (mode === 'baseline') {
            // Same rendered page/layout, restoring the old eager, main-URL behavior.
            await covers.evaluateAll((elements) => {
              for (const element of elements) (element as HTMLImageElement).loading = 'eager'
            })
          }
          // Deliberate measurement window: fixed local mocks, no polling/background requests.
          // eslint-disable-next-line playwright/no-networkidle
          await page.waitForLoadState('networkidle')
          const initial = requested.size
          counts.push(initial)
          if (mode === 'baseline') expect(initial).toBe(12)
          else expect([...requested].every((path) => path.includes('/thumb-'))).toBe(true)
          // Browsers may prefetch near-viewport images. Do not assume only visible items load.
          if (mode === 'optimized') {
            for (let i = 0; i < 12; i++) {
              await covers.nth(i).scrollIntoViewIfNeeded()
              await expect.poll(() => covers.nth(i).evaluate((img) => (img as HTMLImageElement).naturalWidth))
                .toBeGreaterThan(0)
            }
          }
          await expect.poll(() => requested.size).toBe(12)
          results.push({
            width, run, mode, initialRequests: initial, afterViewingAll: requested.size,
            fixtureBodyBytesPerImage: image.length,
          })
        } finally {
          await context.close()
        }
      }
      expect(counts[1]).toBeLessThanOrEqual(counts[0]!)
      // A small page may fit inside the browser's prefetch margin even on mobile.
      // Record that result rather than assuming lazy loading must reduce this count.
    }
  }
  const report = JSON.stringify({ note: 'Local mocked API, identical SVG fixtures; not real COS throughput.', results }, null, 2)
  console.log(report)
  await testInfo.attach('image-delivery-comparison.json', { body: report, contentType: 'application/json' })
})
