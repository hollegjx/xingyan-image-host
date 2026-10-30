import { test, expect, type Page } from '@playwright/test'
import { coverSvg } from '../dev/design-preview.js'

const rows = Array.from({ length: 12 }, (_, index) => ({
  id: String(900100 + index),
  name: [
    '山野之间 · 留一片宁静',
    '光与拱门的对话',
    '向上生长的日常',
    'LESS / 少即是多',
    '海岸线上的漫游',
    'FORM — 形状实验',
    '留白，是另一种表达',
    '慢一点，也很好',
    '夜色里的山',
    '小小的植物图鉴',
    'LIGHT / 光的形状',
    '日常几何',
  ][index],
  category: ['自然', '建筑', '插画', '海报'][index % 4],
  tags: ['极简'],
  thumbnailUrl: `/fixture-${index}.svg`,
  url: `/fixture-${index}.svg`,
  picFormat: 'svg',
  uploadStatus: 1,
  reviewStatus: 1,
  user: { userName: '设计示例' },
}))
async function setup(
  page: Page,
  options: { empty?: boolean; error?: boolean; anonymous?: boolean } = {},
) {
  const calls: Record<string, unknown>[] = []
  await page.route('**/fixture-*.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: coverSvg(Number(/fixture-(\d+)/.exec(route.request().url())?.[1] || 0)),
    }),
  )
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const body = route.request().postDataJSON() || {}
    let data: unknown = {},
      code = 0,
      message = ''
    if (path === '/api/user/get/login')
      data = options.anonymous ? null : { id: '1', userName: '设计示例', userRole: 0 }
    if (path === '/api/picture/tag_category')
      data = { categoryList: ['自然', '建筑', '插画', '海报'], tagList: ['极简', '绿色'] }
    if (path === '/api/picture/list/page/vo') {
      calls.push(body)
      const filtered = rows.filter(
        (row) =>
          (!body.category || body.category === row.category) &&
          (!body.searchText || row.name?.includes(body.searchText)),
      )
      data = { records: options.empty ? [] : filtered, total: options.empty ? 0 : filtered.length }
      if (options.error) {
        code = 50000
        message = '暂时无法连接图库'
        data = null
      }
    }
    await route.fulfill({ json: { code, data, message } })
  })
  return calls
}

test('desktop gallery layout, navigation and working filters', async ({ page }, info) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  const calls = await setup(page)
  await page.goto('/')
  await expect(page.locator('.gallery-card')).toHaveCount(12)
  await expect(page.getByRole('navigation', { name: '主导航' })).toBeVisible()
  await expect(page.locator('.workspace-footer')).toHaveCSS('position', 'static')
  await expect(page.locator('.upload-link')).toHaveCSS('background-color', 'rgb(29, 29, 31)')
  await expect(page.locator('.gallery-card img').first()).toHaveCSS('filter', 'none')
  await expect(page.locator('.gallery-card img').first()).toHaveCSS('opacity', '1')
  await expect(page.locator('.picture-grid')).toHaveCSS('filter', 'none')
  await expect(page.locator('.hero-copy')).toHaveCSS('text-align', 'center')
  await page.screenshot({ path: info.outputPath('gallery-desktop.png'), fullPage: true })
  await page.screenshot({ path: info.outputPath('gallery-desktop-viewport.png') })
  await page.getByRole('button', { name: '建筑', exact: true }).click()
  await expect(page.locator('.gallery-card')).toHaveCount(3)
  expect(calls.at(-1)?.category).toBe('建筑')
  await page.getByRole('button', { name: '清除筛选' }).click()
  await page.getByRole('textbox', { name: '搜索图片' }).fill('山野')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.locator('.gallery-card')).toHaveCount(1)
  await page.getByRole('button', { name: '紧凑布局' }).click()
  await expect(page.locator('.picture-grid')).toHaveClass(/compact/)
  await page.getByRole('button', { name: '标签筛选' }).click()
  await page.getByRole('button', { name: '# 极简' }).click()
  expect(calls.at(-1)?.tags).toEqual(['极简'])
})

test('mobile navigation, gallery and share dialog fit the viewport', async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setup(page)
  await page.goto('/')
  await expect(page.locator('.gallery-card')).toHaveCount(12)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('gallery-mobile.png'), fullPage: true })
  await page.screenshot({ path: info.outputPath('gallery-mobile-viewport.png') })
  await page.getByRole('button', { name: '打开导航' }).click()
  await expect(page.getByRole('navigation', { name: '移动导航' })).toBeVisible()
  await page
    .getByRole('navigation', { name: '移动导航' })
    .getByRole('link', { name: '探索图库' })
    .click()
  await expect(page.getByRole('navigation', { name: '移动导航' })).toBeHidden()
  await page.getByRole('button', { name: '分享', exact: true }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('empty and error states are distinct, without fabricated production records', async ({
  page,
}, info) => {
  await setup(page, { empty: true, anonymous: true })
  await page.goto('/')
  await expect(page.getByText('屋檐已备好，等第一张图片。')).toBeVisible()
  await expect(page.getByRole('link', { name: '上传第一张图片' })).toBeVisible()
  await expect(page.locator('.gallery-card')).toHaveCount(0)
  await page.screenshot({ path: info.outputPath('gallery-empty.png'), fullPage: true })
  await page.unroute('**/api/**')
  await setup(page, { error: true })
  await page.reload()
  await expect(page.getByText('暂时无法连接图库')).toBeVisible()
  await expect(page.getByRole('button', { name: '重试' })).toBeVisible()
  await expect(page.locator('.gallery-empty')).toHaveCount(0)
})

test('login and register forms remain responsive and accessible', async ({ page }, info) => {
  await setup(page, { anonymous: true })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/user/login')
  await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  await expect(page.getByLabel('账号', { exact: true })).toBeVisible()
  await page.screenshot({ path: info.outputPath('login-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByRole('button', { name: '登录', exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.getByRole('link', { name: '去注册' }).click()
  await expect(page.getByRole('heading', { name: '在星檐，留一处空间' })).toBeVisible()
  await expect(page.getByLabel('确认密码', { exact: true })).toBeVisible()
  await page.screenshot({ path: info.outputPath('register-mobile.png'), fullPage: true })
})

test('neutral layout fits small phones, tablets and compact desktops', async ({ page }) => {
  await setup(page)
  await page.goto('/')
  await expect(page.locator('.gallery-card')).toHaveCount(12)
  for (const width of [320, 768, 1024]) {
    await page.setViewportSize({ width, height: 900 })
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  }
})

test('ambient background animates behind content, can be disabled, and respects reduced motion', async ({
  page,
}, info) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await setup(page)
  await page.goto('/')
  const canvas = page.locator('.particle-background')
  await expect(canvas).toBeVisible()
  await expect(canvas).toHaveCSS('pointer-events', 'none')
  await expect(canvas).toHaveAttribute('aria-hidden', 'true')
  const snapshot = () => canvas.evaluate((element) => (element as HTMLCanvasElement).toDataURL())
  const initial = await snapshot()
  await expect.poll(snapshot).not.toBe(initial)
  await page.mouse.move(950, 380, { steps: 12 })
  await page.screenshot({ path: info.outputPath('ambient-desktop.png') })
  // The decorative canvas must not intercept real controls.
  await page.getByRole('button', { name: '建筑', exact: true }).click()
  await expect(page.locator('.gallery-card')).toHaveCount(3)
  await page.getByRole('button', { name: '背景动效：开', exact: true }).click()
  await expect(canvas).toBeHidden()
  await page.reload()
  await expect(canvas).toBeHidden()
  await page.getByRole('button', { name: '背景动效：关', exact: true }).click()
  await expect(canvas).toBeVisible()
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await expect(page.getByRole('button', { name: '背景粒子：静态', exact: true })).toBeVisible()
  const staticFrame = await snapshot()
  // Wait for several browser frames without depending on wall-clock sleeps.
  await page.evaluate(
    () =>
      new Promise<void>((resolve) => {
        let remaining = 5
        const next = () => {
          if (--remaining <= 0) resolve()
          else requestAnimationFrame(next)
        }
        requestAnimationFrame(next)
      }),
  )
  expect(await snapshot()).toBe(staticFrame)
})
