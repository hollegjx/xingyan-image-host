import { expect, test, type Page } from '@playwright/test'

const id = '9007199254740997'
const source = {
  id,
  name: '公开测试图片',
  userId: '1',
  uploadStatus: 1,
  reviewStatus: 1,
  url: '/fixture.png',
}

async function setup(
  page: Page,
  mode: 'success' | 'empty' | 'failure' | 'private' | 'anonymous' = 'success',
) {
  const requests: { path: string; body: Record<string, unknown> }[] = []
  await page.route('https://images.example/**', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180"><rect width="180" height="180" fill="#97b7a2"/></svg>',
    }),
  )
  await page.route('**/fixture.png', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="180" height="180"><circle cx="90" cy="90" r="70" fill="#97b7a2"/></svg>',
    }),
  )
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/picture/capabilities') return route.fulfill({ json: { code: 0, data: { privateStorageConfigured: true, externalImageSearchEnabled: true } } })
    const body = route.request().postData() ? route.request().postDataJSON() : {}
    requests.push({ path, body })
    let data: unknown = {}
    let code = 0
    let message = ''
    if (path === '/api/user/get/login') {
      data = { id: '1', userRole: 0, userName: '搜图测试用户' }
      if (mode === 'anonymous') {
        data = null
        code = 40100
      }
    } else if (path === '/api/picture/get/vo')
      data = { ...source, ...(mode === 'private' ? { spaceId: '7' } : {}) }
    else if (path === '/api/picture/search/picture') {
      data = [
        { thumbUrl: 'https://images.example/a.png', fromUrl: 'https://example.com/source' },
        { thumbUrl: 'javascript:alert(1)', fromUrl: 'https://example.com/evil' },
      ]
      if (mode === 'empty') data = []
      if (mode === 'failure') {
        data = null
        code = 50300
        message = '以图搜图尚未开放，第三方服务验收完成后启用'
      }
    }
    await route.fulfill({ json: { code, data, message } })
  })
  return requests
}

test('explicit consent, long ID, safe external results and mobile layout', async ({
  page,
}, info) => {
  const requests = await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`/picture/search?pictureId=${id}`)
  await expect(page.getByRole('heading', { name: source.name })).toBeVisible()
  const button = page.getByRole('button', { name: '开始搜索' })
  await expect(button).toBeDisabled()
  expect(requests.some((r) => r.path === '/api/picture/search/picture')).toBe(false)
  await page.getByRole('checkbox').check()
  await button.click()
  const link = page.getByRole('link', { name: /查看来源 1/ })
  await expect(link).toHaveAttribute('rel', 'noopener noreferrer')
  await expect(link).toHaveAttribute('href', 'https://example.com/source')
  await expect(page.locator('.results a')).toHaveCount(1)
  await expect(page.locator('.results img')).toHaveAttribute('referrerpolicy', 'no-referrer')
  expect(requests.find((r) => r.path === '/api/picture/search/picture')?.body).toEqual({
    pictureId: id,
    externalSearchConsent: true,
  })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await expect(page.locator('.footer')).toHaveCSS('position', 'static')
  await page.screenshot({
    path: info.outputPath('image-search-mobile.png'),
    fullPage: true,
    animations: 'disabled',
  })
})

test('private images never trigger external search', async ({ page }) => {
  const requests = await setup(page, 'private')
  await page.goto(`/picture/search?pictureId=${id}`)
  await expect(page.getByText('仅支持已审核通过的公开图片')).toBeVisible()
  await expect(page.getByRole('checkbox')).toHaveCount(0)
  expect(requests.some((r) => r.path === '/api/picture/search/picture')).toBe(false)
})

test('provider unavailable is an error, not zero matches; retry stays manual', async ({ page }) => {
  const requests = await setup(page, 'failure')
  await page.goto(`/picture/search?pictureId=${id}`)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '开始搜索' }).click()
  await expect(page.getByText('以图搜图尚未开放，第三方服务验收完成后启用')).toBeVisible()
  await expect(page.getByText('未找到相似图片')).toHaveCount(0)
  expect(requests.filter((r) => r.path === '/api/picture/search/picture')).toHaveLength(1)
})

test('empty response has distinct empty state', async ({ page }) => {
  await setup(page, 'empty')
  await page.goto(`/picture/search?pictureId=${id}`)
  await page.getByRole('checkbox').check()
  await page.getByRole('button', { name: '开始搜索' }).click()
  await expect(page.getByText('未找到相似图片')).toBeVisible()
})

test('invalid ID never loads picture or searches', async ({ page }) => {
  const requests = await setup(page)
  await page.goto('/picture/search?pictureId=9007199254740999999999')
  await expect(page.getByText('图片 ID 无效')).toBeVisible()
  expect(requests.some((r) => r.path.startsWith('/api/picture/'))).toBe(false)
})

test('anonymous visitor returns to login and does not search', async ({ page }) => {
  const requests = await setup(page, 'anonymous')
  await page.goto(`/picture/search?pictureId=${id}`)
  await expect(page).toHaveURL(/\/user\/login\?redirect=/)
  expect(requests.some((r) => r.path.startsWith('/api/picture/'))).toBe(false)
})
