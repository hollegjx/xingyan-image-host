import { test, expect, type Page } from '@playwright/test'
import { coverSvg } from '../dev/design-preview.js'

const user = {
  id: '9007199254740993',
  userName: '林间',
  userAccount: 'gallery-reader',
  userProfile: '收藏日常的光，也分享偶然的灵感。',
  userRole: 0,
  createTime: '2026-10-01T09:00:00+08:00',
}
const rows = Array.from({ length: 18 }, (_, index) => ({
  id: String(900500 + index),
  name: ['山野之间', '光与拱门', '向上生长', '形状实验'][index % 4] + ` · ${index + 1}`,
  category: ['自然', '建筑', '插画', '海报'][index % 4],
  userId: user.id,
  url: `/account-art/${index}.svg`,
  thumbnailUrl: `/account-art/${index}.svg`,
  uploadStatus: index % 4 === 1 ? 0 : 1,
  reviewStatus: index % 4 === 2 ? 0 : index % 4 === 3 ? 2 : 1,
  reviewMessage: index % 4 === 3 ? '请补充作品的来源说明，再提交审核。' : '',
  createTime: '2026-10-25T09:00:00+08:00',
  picWidth: 480,
  picHeight: 350,
  picSize: 2048,
}))
async function setup(
  page: Page,
  options: {
    empty?: boolean
    listError?: boolean
    anonymous?: boolean
    profileError?: boolean
    logoutError?: boolean
  } = {},
) {
  const calls: { path: string; body: Record<string, unknown> }[] = []
  let profileReads = 0
  await page.route('**/account-art/*.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: coverSvg(Number(/\/(\d+)\.svg/.exec(route.request().url())?.[1] || 0)),
    }),
  )
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const body = route.request().postDataJSON() || {}
    calls.push({ path, body })
    let data: unknown = null,
      code = 0,
      message = ''
    if (path === '/api/user/get/login') {
      profileReads++
      data = options.anonymous ? null : user
      if (options.profileError && profileReads > 1) {
        code = 50000
        message = '资料服务暂不可用'
      }
    } else if (path === '/api/picture/my/list/page/vo') {
      const filtered = rows.filter(
        (row) =>
          (body.uploadStatus === undefined || row.uploadStatus === body.uploadStatus) &&
          (body.reviewStatus === undefined || row.reviewStatus === body.reviewStatus) &&
          (!body.searchText || row.name.includes(body.searchText)),
      )
      data = {
        records: options.empty
          ? []
          : filtered.slice((body.current - 1) * body.pageSize, body.current * body.pageSize),
        total: options.empty ? 0 : filtered.length,
      }
      if (options.listError) {
        code = 50000
        message = '投稿暂时不可用'
      }
    } else if (path === '/api/user/logout') {
      data = !options.logoutError
      if (options.logoutError) {
        code = 50000
        message = '退出失败，请重试'
      }
    } else if (path === '/api/picture/get/vo')
      data = rows.find((row) => row.id === new URL(route.request().url()).searchParams.get('id'))
    else if (path === '/api/picture/tag_category') data = { categoryList: [], tagList: [] }
    else {
      code = 50000
      message = '未配置的测试接口'
    }
    await route.fulfill({ json: { code, data, message } })
  })
  return calls
}

test('submissions communicate scope, statuses, filters, pagination and edit destination', async ({
  page,
}, info) => {
  const calls = await setup(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/picture/mine')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('我的投稿。')
  await expect(page.locator('.submission-card')).toHaveCount(12)
  await expect(page.getByRole('link', { name: /寻找私人图片/ })).toHaveAttribute(
    'href',
    '/space/mine',
  )
  await expect(page.locator('.submission-card img').first()).toHaveCSS('filter', 'none')
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('.submission-card h2').first()).toBeInViewport()
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.screenshot({ path: info.outputPath('submissions-desktop.png'), fullPage: true })
  await page.screenshot({ path: info.outputPath('submissions-desktop-viewport.png') })
  await page.getByTitle('下一页').click()
  await expect(page.locator('.submission-card')).toHaveCount(6)
  await page.getByRole('button', { name: '草稿', exact: true }).click()
  await expect(page.locator('.submission-card')).toHaveCount(5)
  expect(calls.filter((call) => call.path.includes('/my/list/')).at(-1)?.body).toMatchObject({
    current: 1,
    uploadStatus: 0,
  })
  await expect(
    page.getByRole('link', { name: '继续完善：光与拱门 · 2', exact: true }),
  ).toHaveAttribute('href', '/picture/add?id=900501')
  await page.getByRole('button', { name: '需修改', exact: true }).click()
  await expect(page.locator('.submission-card')).toHaveCount(4)
  await expect(page.locator('.submission-hint').first()).toHaveText(
    '请补充作品的来源说明，再提交审核。',
  )
  expect(calls.filter((call) => call.path.includes('/my/list/')).at(-1)?.body).toMatchObject({
    uploadStatus: 1,
    reviewStatus: 2,
  })
  await page.getByRole('button', { name: '全部作品', exact: true }).click()
  await page.getByRole('textbox', { name: '搜索我的投稿' }).fill('山野')
  await page.getByRole('button', { name: '搜索投稿', exact: true }).click()
  await expect(page.locator('.submission-card')).toHaveCount(5)
  await page.getByLabel('投稿排序').selectOption('editTime')
  await expect(page).toHaveURL(/order=editTime/)
  await page.reload()
  await expect(page.getByRole('textbox', { name: '搜索我的投稿' })).toHaveValue('山野')
  await expect(page.locator('.submission-card')).toHaveCount(5)
  for (const call of calls.filter((call) => call.path.includes('/my/list/'))) {
    expect(call.body).not.toHaveProperty('spaceId')
    expect(call.body).not.toHaveProperty('userId')
  }
})

test('empty, filtered-empty and error have honest distinct recovery actions', async ({ page }) => {
  const options = { empty: true, listError: false }
  await setup(page, options)
  await page.goto('/picture/mine')
  await expect(page.getByText('让第一件作品，被看见。')).toBeVisible()
  await page.getByRole('button', { name: '草稿', exact: true }).click()
  await expect(page.getByText('这里还没有匹配的作品')).toBeVisible()
  await page.getByRole('button', { name: '查看全部投稿' }).click()
  options.listError = true
  await page.reload()
  await expect(page.getByText('投稿暂时不可用')).toBeVisible()
  await expect(page.getByText('让第一件作品，被看见。')).toHaveCount(0)
  options.listError = false
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByText('让第一件作品，被看见。')).toBeVisible()
})

test('new filters win over a late earlier response', async ({ page }) => {
  await setup(page)
  await page.goto('/picture/mine')
  await expect(page.locator('.submission-card')).toHaveCount(12)
  let release: () => void = () => {}
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/picture/my/list/page/vo', async (route) => {
    const body = route.request().postDataJSON()
    if (body.uploadStatus === 0) {
      await gate
      await route.fulfill({ json: { code: 0, data: { records: [rows[1]], total: 1 } } })
    } else await route.fallback()
  })
  const request = page.waitForRequest(
    (req) => req.url().endsWith('/my/list/page/vo') && req.postDataJSON().uploadStatus === 0,
  )
  await page.getByRole('button', { name: '草稿', exact: true }).click()
  await request
  await page.getByRole('button', { name: '已公开', exact: true }).click()
  await expect(page.locator('.submission-card')).toHaveCount(5)
  const response = page.waitForResponse(
    (res) =>
      res.url().endsWith('/my/list/page/vo') && res.request().postDataJSON().uploadStatus === 0,
  )
  release()
  await response
  await expect(page.locator('.submission-card')).toHaveCount(5)
  await expect(page.locator('.submission-status').first()).toHaveText('已公开')
})

test('avatar opens real current-user profile without admin APIs', async ({ page }, info) => {
  const calls = await setup(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/picture/mine')
  await page.getByRole('button', { name: '用户菜单' }).click()
  await page.getByRole('menuitem', { name: '个人资料' }).click()
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('个人资料。')
  await expect(page.getByText('9007199254740993', { exact: true })).toBeVisible()
  await expect(page.getByText('gallery-reader', { exact: true })).toBeVisible()
  await expect(page.getByText('当前资料由管理员维护', { exact: false })).toBeVisible()
  await expect(page.locator('.profile-identity').getByRole('heading')).toHaveText('林间')
  await expect(page.getByRole('menu')).toBeHidden()
  await page.screenshot({ path: info.outputPath('profile-desktop.png'), fullPage: true })
  expect(
    calls.some((call) =>
      ['/api/user/update', '/api/user/get', '/api/user/list/page/vo'].includes(call.path),
    ),
  ).toBe(false)
})

test('profile failure does not masquerade as editable or empty data and can retry', async ({
  page,
}) => {
  const options = { profileError: true }
  await setup(page, options)
  await page.goto('/user/profile')
  await expect(page.getByText('资料服务暂不可用')).toBeVisible()
  await expect(page.locator('.profile-fields')).toHaveCount(0)
  options.profileError = false
  await page.getByRole('button', { name: '重新加载' }).click()
  await expect(page.getByText('gallery-reader', { exact: true })).toBeVisible()
})

test('settings apply, persist and synchronize with footer without user mutation calls', async ({
  page,
}, info) => {
  const calls = await setup(page)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.goto('/user/settings')
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('偏好与设置。')
  await page.screenshot({ path: info.outputPath('settings-desktop.png'), fullPage: true })
  await page.getByRole('switch', { name: '背景粒子', exact: true }).click()
  await expect(page.locator('.particle-background')).toBeHidden()
  await expect(page.getByRole('button', { name: '背景动效：关', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '背景动效：关', exact: true }).click()
  await expect(page.getByRole('switch', { name: '背景粒子', exact: true })).toBeChecked()
  await page.getByRole('switch', { name: '减少动态效果', exact: true }).click()
  await expect(page.locator('html')).toHaveAttribute('data-reduce-motion', 'true')
  await expect(page.getByRole('button', { name: '背景粒子：静态', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('switch', { name: '减少动态效果', exact: true })).toBeChecked()
  await page.getByRole('button', { name: '恢复默认', exact: true }).click()
  await expect(page.getByRole('switch', { name: '减少动态效果', exact: true })).not.toBeChecked()
  expect(calls.every((call) => call.path === '/api/user/get/login')).toBe(true)
})

test('logout failure preserves identity; success clears it and returns to login', async ({
  page,
}) => {
  const options = { logoutError: true }
  const calls = await setup(page, options)
  await page.goto('/user/settings')
  await page.getByRole('button', { name: '退出当前登录' }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('退出失败，请重试')
  await expect(page.getByRole('button', { name: '用户菜单' })).toBeVisible()
  options.logoutError = false
  await page.getByRole('button', { name: '退出当前登录' }).click()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await expect(page).toHaveURL(/\/user\/login$/)
  await expect(page.getByRole('button', { name: '用户菜单' })).toHaveCount(0)
  expect(calls.filter((call) => call.path === '/api/user/logout')).toHaveLength(2)
})

test('account routes require login and preserve return destination', async ({ page }) => {
  await setup(page, { anonymous: true })
  await page.goto('/user/settings')
  await expect(page).toHaveURL(/\/user\/login\?redirect=/)
  await expect(page.getByRole('switch')).toHaveCount(0)
  await page.goto('/user/profile')
  await expect(page).toHaveURL(/\/user\/login\?redirect=/)
  await expect(page.locator('.profile-fields')).toHaveCount(0)
})

test('all three pages fit small phones, tablets and desktops', async ({ page }, info) => {
  await setup(page)
  for (const { path, ready } of [
    { path: '/picture/mine', ready: '.submission-card' },
    { path: '/user/profile', ready: '.profile-fields' },
    { path: '/user/settings', ready: '.setting-row' },
  ]) {
    await page.goto(path)
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.locator(ready).first()).toBeVisible()
    for (const width of [320, 390, 768, 1440]) {
      await page.setViewportSize({ width, height: 844 })
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      )
    }
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({
      path: info.outputPath(`${path.split('/').at(-1)}-mobile.png`),
      fullPage: true,
    })
    await page.screenshot({
      path: info.outputPath(`${path.split('/').at(-1)}-mobile-viewport.png`),
    })
  }
})
