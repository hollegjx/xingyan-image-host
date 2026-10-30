import { test, expect, type Page } from '@playwright/test'
async function mockFavorites(page: Page, loggedIn = true) {
  let favorited = false
  const writes: boolean[] = []
  const picture = {
    id: '10',
    name: '收藏测试作品',
    userId: '2',
    uploadStatus: 1,
    reviewStatus: 1,
    url: '/favicon.ico',
  }
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/user/get/login')
      return route.fulfill({
        json: loggedIn
          ? { code: 0, data: { id: '1', userName: '收藏用户', userRole: 0 } }
          : { code: 40100 },
      })
    let data: unknown = {}
    if (path === '/api/picture/get/vo')
      data = {
        ...picture,
        favorited: loggedIn ? favorited : undefined,
        favoriteCount: favorited ? 1 : 0,
      }
    if (path === '/api/picture/favorite/set') {
      favorited = route.request().postDataJSON().favorite
      writes.push(favorited)
      data = { favorited, favoriteCount: favorited ? 1 : 0 }
    }
    if (path === '/api/picture/favorite/mine')
      data = { records: favorited ? [picture] : [], total: favorited ? 1 : 0 }
    if (path === '/api/picture/capabilities') data = { externalImageSearchEnabled: false }
    return route.fulfill({ json: { code: 0, data } })
  })
  return writes
}
test('favorite button updates count, user menu opens favorites and removal clears it', async ({
  page,
}) => {
  const writes = await mockFavorites(page)
  await page.goto('/picture/10')
  const favorite = page.getByRole('button', { name: '收藏 · 0', exact: true })
  await favorite.click()
  await expect(page.getByRole('button', { name: '已收藏 · 1', exact: true })).toHaveAttribute(
    'aria-pressed',
    'true',
  )
  await page.getByRole('button', { name: '用户菜单' }).click()
  await page.getByRole('menuitem', { name: '我的收藏' }).click()
  await expect(page).toHaveURL(/\/user\/favorites$/)
  await expect(page.getByRole('heading', { name: '收藏测试作品' })).toBeVisible()
  await page.getByRole('button', { name: '取消收藏', exact: true }).click()
  await expect(page.getByText('还没有可展示的收藏，去公共图库看看吧')).toBeVisible()
  expect(writes).toEqual([true, false])
})
test('anonymous favorite click requests login without a mutation', async ({ page }) => {
  const writes = await mockFavorites(page, false)
  await page.goto('/picture/10')
  await page.getByRole('button', { name: '收藏 · 0', exact: true }).click()
  await expect(page).toHaveURL(/\/user\/login\?redirect=/)
  expect(writes).toEqual([])
})
test('my favorites fits a narrow mobile viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await mockFavorites(page)
  await page.goto('/picture/10')
  await page.getByRole('button', { name: '收藏 · 0', exact: true }).click()
  await expect(page.getByRole('button', { name: '已收藏 · 1', exact: true })).toBeVisible()
  await page.goto('/user/favorites')
  await expect(page.getByRole('heading', { name: '我的收藏', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '收藏测试作品' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
})
