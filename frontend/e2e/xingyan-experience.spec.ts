import { test, expect, type Page } from '@playwright/test'
import { coverSvg } from '../dev/design-preview.js'

const runtimeErrors = new WeakMap<Page, string[]>()
test.beforeEach(({ page }) => {
  const errors: string[] = []
  runtimeErrors.set(page, errors)
  page.on('pageerror', (error) => errors.push(error.message))
})
test.afterEach(({ page }) => expect(runtimeErrors.get(page)).toEqual([]))

const uid = '9007199254740993'
const sid = '9007199254740995'
const pictures = Array.from({ length: 36 }, (_, i) => ({
  id: String(910000 + i),
  userId: uid,
  name: `檐下日常 ${i + 1}`,
  url: `/xingyan-art/${i}.svg`,
  thumbnailUrl: `/xingyan-art/${i}.svg`,
  category: i % 2 ? '建筑' : '自然',
  tags: ['日常'],
  reviewStatus: 1,
  uploadStatus: 1,
}))
async function setup(
  page: Page,
  options: { team?: boolean; failSave?: boolean; slowList?: boolean } = {},
) {
  const calls: { path: string; body: Record<string, unknown> }[] = []
  let currentSpace: string | undefined
  await page.route('**/xingyan-art/*.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: coverSvg(Number(/\/(\d+)\.svg/.exec(route.request().url())?.[1] || 0)),
    }),
  )
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    const body = route.request().postDataJSON() || {}
    calls.push({ path, body })
    let data: unknown = {},
      code = 0
    if (path === '/api/user/get/login') data = { id: uid, userName: '檐下访客', userRole: 0 }
    else if (path === '/api/picture/tag_category')
      data = { categoryList: ['自然', '建筑'], tagList: ['日常'] }
    else if (path.includes('/list/page/vo') && path.includes('/picture/')) {
      currentSpace = body.spaceId
      if (options.slowList) await new Promise((resolve) => setTimeout(resolve, 350))
      const rows = pictures.filter(
        (p) =>
          (!body.category || p.category === body.category) &&
          (!body.searchText || p.name.includes(body.searchText)),
      )
      data = {
        records: rows
          .slice((body.current - 1) * body.pageSize, body.current * body.pageSize)
          .map((p) => ({ ...p, ...(body.spaceId ? { spaceId: body.spaceId } : {}) })),
        total: rows.length,
      }
    } else if (path === '/api/picture/get/vo')
      data = {
        ...pictures.find((p) => p.id === url.searchParams.get('id')),
        ...(currentSpace ? { spaceId: currentSpace } : {}),
      }
    else if (path === '/api/picture/edit') {
      data = true
      if (options.failSave) code = 50000
    } else if (path === '/api/space/get/vo')
      data = {
        id: sid,
        spaceType: options.team ? 1 : 0,
        userId: uid,
        spaceName: '檐下小组',
        permissionList: ['picture:view', 'picture:upload', 'picture:edit', 'picture:delete'],
        totalCount: 36,
      }
    else if (path === '/api/spaceUser/my/list/page') data = { records: [], total: 0 }
    await route.fulfill({ json: { code, data, message: code ? '保存暂时失败' : '' } })
  })
  return calls
}
async function settled(page: Page, key: string) {
  await expect(page.locator(`.route-page[data-view="${key}"]`)).not.toHaveClass(/workspace-page-/)
  await expect(page.locator('.route-page [aria-busy="true"]')).toHaveCount(0)
}
function nav(page: Page) {
  return page.getByRole('navigation', { name: '主导航' })
}

test('unsaved form offers cancel/discard, protects refresh, and keeps changes after failed save', async ({
  page,
}) => {
  const options = { failSave: true }
  const calls = await setup(page, options)
  await page.goto('/picture/add?id=910000')
  const input = page.getByPlaceholder('请输入名称', { exact: true })
  await input.fill('还没保存的新名称')
  expect(
    await page.evaluate(
      () => !window.dispatchEvent(new Event('beforeunload', { cancelable: true })),
    ),
  ).toBe(true)
  await nav(page).getByRole('link', { name: '探索图库' }).click()
  await expect(page.getByText('还有未保存的修改')).toBeVisible()
  await page.getByRole('button', { name: '继续编辑', exact: true }).click()
  await expect(page).toHaveURL('/picture/add?id=910000')
  await expect(input).toHaveValue('还没保存的新名称')
  await page.getByRole('button', { name: '保存图片信息' }).click()
  await expect(page.getByText('创建失败：保存暂时失败')).toBeVisible()
  await nav(page).getByRole('link', { name: '探索图库' }).click()
  await expect(page.getByText('还有未保存的修改')).toBeVisible()
  await page.getByRole('button', { name: '放弃修改并离开' }).click()
  await expect(page).toHaveURL('/')
  expect(calls.filter((c) => c.path === '/api/picture/edit')).toHaveLength(1)
  expect(
    await page.evaluate(
      () => !window.dispatchEvent(new Event('beforeunload', { cancelable: true })),
    ),
  ).toBe(false)
})

test('submission detail and successful edit return to the original filtered page', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/picture/mine?page=2&q=檐下&order=editTime')
  await page.locator('.submission-card .submission-cover').first().click()
  await settled(page, 'picture-detail')
  await page.getByRole('button', { name: '返回列表' }).click()
  await expect(page).toHaveURL(/\/picture\/mine\?page=2&q=.*&order=editTime$/)
  await expect(page.getByRole('textbox', { name: '搜索我的投稿' })).toHaveValue('檐下')
  await page
    .locator('.submission-card')
    .first()
    .getByRole('link', { name: /^编辑作品/ })
    .click()
  await page.getByPlaceholder('请输入名称', { exact: true }).fill('保存后回到投稿')
  await page.getByRole('button', { name: '保存图片信息' }).click()
  await expect(page).toHaveURL(/\/picture\/mine\?page=2&q=.*&order=editTime$/)
  await expect(page.getByText('还有未保存的修改')).toHaveCount(0)
})

test('gallery return restores filters, page and scroll after data loads; new pages start at top', async ({
  page,
}) => {
  await setup(page, { slowList: true })
  await page.goto('/')
  await page.getByRole('link', { name: '逛逛公共图库' }).click()
  await expect(page).toHaveURL('/#gallery')
  await page.getByRole('button', { name: '自然', exact: true }).click()
  await page.getByTitle('下一页').click()
  await expect(page.locator('.gallery-card')).toHaveCount(6)
  const link = page.locator('.gallery-card .card-image').last()
  await link.scrollIntoViewIfNeeded()
  const scroll = await page.evaluate(() => scrollY)
  await link.click()
  await settled(page, 'picture-detail')
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0)
  await page.getByRole('button', { name: '返回列表' }).click()
  await expect(page.locator('.gallery-card')).toHaveCount(6)
  await expect(page.getByRole('button', { name: '自然', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  )
  await expect
    .poll(async () => Math.abs((await page.evaluate(() => scrollY)) - scroll))
    .toBeLessThan(4)
  await nav(page).getByRole('link', { name: '我的投稿' }).click()
  await settled(page, 'my-pictures')
  await expect.poll(() => page.evaluate(() => scrollY)).toBe(0)
})

test('space return retains applied filters and only marks the actual team section', async ({
  page,
}) => {
  const calls = await setup(page, { team: true })
  await page.goto(`/space/${sid}`)
  await expect(nav(page).getByRole('link', { name: '团队空间' })).toHaveAttribute(
    'aria-current',
    'page',
  )
  await expect(nav(page).getByRole('link', { name: '我的空间' })).not.toHaveAttribute(
    'aria-current',
    'page',
  )
  await page.getByRole('textbox', { name: '关键词', exact: true }).fill('檐下')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await page.getByTitle('下一页').click()
  await page.locator('.space-picture-card').first().getByRole('link').click()
  await settled(page, 'picture-detail')
  await page.getByRole('button', { name: '返回列表' }).click()
  await expect(page.getByRole('textbox', { name: '关键词', exact: true })).toHaveValue('檐下')
  await expect
    .poll(() => calls.filter((c) => c.path === '/api/picture/list/page/vo').at(-1)?.body)
    .toMatchObject({ current: 2, searchText: '檐下', spaceId: sid })
  await expect(nav(page).getByRole('link', { name: '团队空间' })).toHaveAttribute(
    'aria-current',
    'page',
  )
})

test('direct public detail uses home fallback and public navigation', async ({ page }) => {
  await setup(page)
  await page.goto('/picture/910000')
  await expect(nav(page).getByRole('link', { name: '探索图库' })).toHaveAttribute(
    'aria-current',
    'page',
  )
  await page.getByRole('button', { name: '返回列表' }).click()
  await expect(page).toHaveURL('/')
})

test('404 has a recovery action, not an empty shell', async ({ page }, info) => {
  await setup(page)
  await page.goto('/missing-page')
  await expect(page.getByRole('heading', { name: '这片屋檐下，暂时没有这一页' })).toBeVisible()
  await expect(page).toHaveTitle('页面未找到 · 星檐图床')
  await page.screenshot({ path: info.outputPath('xingyan-404.png') })
  await page.getByRole('link', { name: '返回首页' }).click()
  await expect(page).toHaveURL('/')
})

test('brand stays readable on desktop and small phones, with full-color pictures and working CTAs', async ({
  page,
}, info) => {
  await setup(page)
  await page.goto('/')
  await expect(page).toHaveTitle('探索图库 · 星檐图床')
  await expect(page.locator('link[rel="icon"]')).toHaveAttribute('href', '/favicon.svg')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('为每一张图片，留一处屋檐。')
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 950 })
    await expect(page.getByRole('link', { name: '收好我的图片' })).toBeInViewport()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.screenshot({ path: info.outputPath(`xingyan-home-${width}.png`) })
  }
  await page.getByRole('link', { name: '逛逛公共图库' }).click()
  await expect(page).toHaveURL('/#gallery')
  await expect(page.locator('#gallery')).toBeInViewport()
  await expect(page.locator('.gallery-card img').first()).toHaveCSS('filter', 'none')
  await page.goto('/user/login')
  await expect(page.getByText('回到星檐，接着收好你的日常。')).toBeVisible()
  await page.getByRole('link', { name: '去注册' }).click()
  await expect(page.getByRole('heading', { name: '在星檐，留一处空间' })).toBeVisible()
})
