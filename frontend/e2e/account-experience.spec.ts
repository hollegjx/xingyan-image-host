import { test, expect, type Page } from '@playwright/test'

async function setup(page: Page, anonymous = false) {
  let favorite = false
  const writes: boolean[] = []
  const picture = { id: '10', userId: '2', name: '山间测试', uploadStatus: 1, reviewStatus: 1, url: '/favicon.ico' }
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/user/get/login') return route.fulfill({ json: anonymous ? { code: 40100 } : { code: 0, data: { id: '1', userName: '测试用户' } } })
    let data: unknown = {}
    if (path === '/api/picture/list/page/vo') data = { records: [picture], total: 1 }
    if (path === '/api/picture/tag_category') data = { tagList: [], categoryList: [] }
    if (path === '/api/picture/capabilities') data = { privateStorageConfigured: true, externalImageSearchEnabled: false }
    if (path === '/api/picture/favorite/status') data = { '10': { favoriteCount: favorite ? 3 : 2, ...(anonymous ? {} : { favorited: favorite }) } }
    if (path === '/api/picture/favorite/set') {
      favorite = route.request().postDataJSON().favorite
      writes.push(favorite)
      data = { favoriteCount: favorite ? 3 : 2, favorited: favorite }
    }
    if (path === '/api/picture/get/vo') data = { ...picture, favoriteCount: 2, favorited: false }
    if (path === '/api/space/my/list/page/vo') data = { records: [], total: 0 }
    if (path === '/api/space/get/vo') data = { id: '99', spaceName: '测试空间', userId: '1', spaceType: 0, permissionList: ['picture:view'], totalCount: 1 }
    await route.fulfill({ json: { code: 0, data } })
  })
  return writes
}

test('card favorites update counts and give feedback without navigating away', async ({ page }) => {
  const writes = await setup(page)
  await page.goto('/')
  await expect(page.locator('.topbar').getByRole('link', { name: '我的收藏' })).toBeVisible()
  const favorite = page.getByRole('button', { name: '收藏 山间测试', exact: true })
  await expect(favorite).toContainText('2')
  await favorite.click()
  await expect(page.getByText('已收藏', { exact: true })).toBeVisible()
  const remove = page.getByRole('button', { name: '取消收藏 山间测试', exact: true })
  await expect(remove).toContainText('3')
  await remove.click()
  await expect(page.getByText('已取消收藏', { exact: true })).toBeVisible()
  await expect(favorite).toContainText('2')
  await expect(page).toHaveURL(/\/$/)
  expect(writes).toEqual([true, false])
  await page.setViewportSize({ width: 390, height: 844 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: 'test-results/account-experience-mobile.png', fullPage: true, animations: 'disabled' })
})
test('anonymous card favorite goes to login without writing', async ({ page }) => {
  const writes = await setup(page, true)
  await page.goto('/')
  await page.getByRole('button', { name: '收藏 山间测试', exact: true }).click()
  await expect(page).toHaveURL(/\/user\/login\?redirect=/)
  expect(writes).toEqual([])
})
test('initial login lookup hides both identity choices and shows a page skeleton', async ({ page }) => {
  await setup(page)
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/user/get/login', async route => { await gate; await route.fulfill({ json: { code: 0, data: { id: '1', userName: '测试用户' } } }) })
  await page.goto('/picture/10')
  await expect(page.getByRole('status', { name: '正在加载页面' })).toBeVisible()
  await expect(page.locator('.topbar').getByText('登录 / 注册')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '用户菜单' })).toHaveCount(0)
  release()
  await expect(page.getByRole('button', { name: '用户菜单' })).toBeVisible()
  await expect(page.getByRole('status', { name: '正在加载页面' })).toHaveCount(0)
  await expect(page.getByText('以图搜图暂未开放', { exact: true })).toBeVisible()
})
for (const [path, endpoint, label] of [
  ['/picture/10', '**/api/picture/get/vo?*', '正在加载图片'],
  ['/space/mine', '**/api/space/my/list/page/vo', '正在加载我的空间'],
  ['/space/99', '**/api/space/get/vo?*', '正在加载空间'],
]) {
  test(`loading skeleton remains visible on ${path}`, async ({ page }) => {
    await setup(page)
    let release!: () => void
    const gate = new Promise<void>(resolve => { release = resolve })
    await page.route(endpoint!, async route => { await gate; await route.fallback() })
    await page.goto(path!)
    await expect(page.getByRole('status', { name: label })).toBeVisible()
    release()
    await expect(page.getByRole('status', { name: label })).toHaveCount(0)
  })
}
test('empty filtered gallery has one reset and buttons have no automatic spaces', async ({ page }) => {
  await setup(page)
  await page.route('**/api/picture/list/page/vo', route => route.fulfill({ json: { code: 0, data: { records: [], total: 0 } } }))
  await page.goto('/')
  await page.getByRole('textbox', { name: '搜索图片' }).fill('不存在')
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByRole('button', { name: '清除筛选' })).toHaveCount(1)
  await page.goto('/user/login')
  await expect(page.getByRole('button', { name: '登录', exact: true })).toHaveText('登录')
  await page.getByRole('button', { name: '忘记密码？' }).click()
  await expect(page.getByText('请联系站点管理员', { exact: true })).toBeVisible()
})

test('static confirmation buttons also keep their Chinese text unspaced', async ({ page }) => {
  await setup(page)
  await page.route('**/api/user/get/login', route => route.fulfill({ json: { code: 0, data: { id: '1', userRole: 1, userName: '测试管理员' } } }))
  await page.goto('/picture/10')
  await page.getByRole('button', { name: 'delete 删除', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: '取消', exact: true })).toHaveText('取消')
  await expect(dialog.getByRole('button', { name: '确认', exact: true })).toHaveText('确认')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
})
