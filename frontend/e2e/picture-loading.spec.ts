import { test, expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'

const image = readFileSync(new URL('../src/assets/logo.svg', import.meta.url))
const records = [
  { id: 1, name: '缩略图图片', url: '/fixtures/main-1.svg', thumbnailUrl: '/fixtures/thumb-1.svg' },
  { id: 2, name: '历史图片', url: '/fixtures/main-2.svg' },
  { id: 3, name: '缩略图失效', url: '/fixtures/main-3.svg', thumbnailUrl: '/fixtures/broken.svg' },
]

async function mockGallery(page: Page, pictures = records) {
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = {}
    if (path === '/api/user/get/login') data = { id: 1, userName: '测试管理员', userRole: 1 }
    else if (path === '/api/picture/tag_category') data = { categoryList: [], tagList: [] }
    else if (path === '/api/picture/get/vo') data = pictures[0]
    else if (path.includes('/list/') || path.includes('/my/list/')) {
      data = { records: pictures, total: pictures.length }
    }
    await route.fulfill({ json: { code: 0, data } })
  })
  await page.route('**/fixtures/**', (route) =>
    route.request().url().endsWith('/broken.svg')
      ? route.fulfill({ status: 404, body: '' })
      : route.fulfill({ contentType: 'image/svg+xml', body: image }),
  )
}

for (const viewport of [{ width: 1280, height: 800 }, { width: 390, height: 844 }]) {
  test(`home thumbnails, legacy fallback and detail at ${viewport.width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    await mockGallery(page)
    const requests: string[] = []
    page.on('request', (request) => requests.push(new URL(request.url()).pathname))
    await page.goto('/')
    const covers = page.locator('#homePage .picture-thumbnail img')
    await expect(covers).toHaveCount(3)
    await expect(covers.nth(0)).toHaveAttribute('src', '/fixtures/thumb-1.svg')
    await expect(covers.nth(1)).toHaveAttribute('src', '/fixtures/main-2.svg')
    await expect(covers.nth(2)).toHaveAttribute('src', '/fixtures/main-3.svg')
    await expect.poll(() => covers.nth(0).evaluate((img) => (img as HTMLImageElement).naturalWidth))
      .toBeGreaterThan(0)
    expect(requests).not.toContain('/fixtures/main-1.svg')
    await page.screenshot({ path: testInfo.outputPath(`home-${viewport.width}.png`), fullPage: true })
    await page.getByRole('link').filter({ has: page.getByAltText('缩略图图片', { exact: true }) }).click()
    await expect(page.locator('.detail-image')).toHaveAttribute('src', '/fixtures/main-1.svg')
  })
}

test('my pictures uses thumbnails; admin opens the main image on demand', async ({ page }) => {
  await mockGallery(page)
  await page.goto('/picture/mine')
  await expect(page.locator('#myPicturePage img').first()).toHaveAttribute('src', '/fixtures/thumb-1.svg')
  await page.goto('/admin/pictureManage')
  const requests: string[] = []
  page.on('request', (request) => requests.push(new URL(request.url()).pathname))
  await expect(page.locator('#pictureManagePage .picture-thumbnail img').first())
    .toHaveAttribute('src', '/fixtures/thumb-1.svg')
  expect(requests).not.toContain('/fixtures/main-1.svg')
  await page.getByRole('button', { name: '预览 缩略图图片', exact: true }).click()
  await expect(page.locator('.ant-image-preview-img')).toBeVisible()
  await expect(page.locator('.ant-image-preview-img')).toHaveAttribute('src', '/fixtures/main-1.svg')
  await page.locator('.ant-image-preview-operations .anticon-close').click()
  await expect(page.locator('.ant-image-preview-img')).toHaveCount(0)
})

test('distant images wait until scrolling brings them near the viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 700 })
  const many = Array.from({ length: 40 }, (_, index) => ({
    id: index + 1, name: `图片 ${index}`, url: `/fixtures/main-${index}.svg`,
    thumbnailUrl: `/fixtures/thumb-${index}.svg`,
  }))
  await mockGallery(page, many)
  const requested = new Set<string>()
  page.on('request', (request) => requested.add(new URL(request.url()).pathname))
  await page.goto('/')
  await expect.poll(() => requested.has('/fixtures/thumb-0.svg')).toBe(true)
  expect(requested.has('/fixtures/thumb-39.svg')).toBe(false)
  const last = page.locator('#homePage .picture-thumbnail img').last()
  await last.scrollIntoViewIfNeeded()
  await expect.poll(() => requested.has('/fixtures/thumb-39.svg')).toBe(true)
  expect(requested.has('/fixtures/main-39.svg')).toBe(false)
})
