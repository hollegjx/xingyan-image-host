import { test, expect, type Page } from '@playwright/test'

async function mockPublication(page: Page, role: 'owner' | 'admin' | 'editor' | 'viewer') {
  const calls: { path: string; body: unknown }[] = []
  const space = {
    id: '20',
    spaceName: '投稿验收空间',
    spaceType: role === 'owner' ? 0 : 1,
    userId: role === 'owner' ? '1' : '2',
    totalCount: '1',
    totalSize: '100',
    maxCount: '100',
    maxSize: '104857600',
    permissionList: ['picture:view', ...(role === 'admin' ? ['spaceUser:manage'] : [])],
  }
  const picture = {
    id: '10',
    spaceId: '20',
    userId: '2',
    uploadStatus: 1,
    reviewStatus: 1,
    name: '空间作品',
    url: '/favicon.ico',
  }
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = {}
    if (path === '/api/user/get/login') data = { id: '1', userName: '当前用户', userRole: 1 }
    if (path === '/api/space/get/vo') data = space
    if (path === '/api/picture/get/vo') data = picture
    if (path === '/api/picture/list/page/vo') data = { records: [picture], total: '1' }
    if (path === '/api/picture/tag_category') data = { tagList: [], categoryList: [] }
    if (path === '/api/picture/capabilities')
      data = { privateStorageConfigured: true, externalImageSearchEnabled: false }
    if (path === '/api/picture/submit/public') {
      calls.push({ path, body: route.request().postDataJSON() })
      data = '101'
    }
    if (path === '/api/picture/private/link') {
      calls.push({ path, body: route.request().postDataJSON() })
      // 无真实签名或凭据，浏览器回归只验证交互生命周期。
      data = { url: 'https://example.invalid/temporary-display', expiresAt: '2030-01-01T00:00:00Z' }
    }
    await route.fulfill({ json: { code: 0, data } })
  })
  return calls
}

test('owner confirms submission from detail menu and gets my submissions link', async ({
  page,
}) => {
  const calls = await mockPublication(page, 'owner')
  await page.goto('/picture/10')
  await page.getByRole('button', { name: '更多 ···' }).click()
  await page.getByRole('menuitem', { name: '投稿到公共图库' }).click()
  await expect(page.getByText('审核通过后所有人可见')).toBeVisible()
  expect(calls).toHaveLength(0)
  await page.getByRole('button', { name: '确认投稿' }).click()
  await expect(page.getByText('投稿成功，等待审核')).toBeVisible()
  await expect(page.getByRole('link', { name: '查看我的投稿' })).toHaveAttribute(
    'href',
    '/picture/mine',
  )
  expect(calls).toEqual([{ path: '/api/picture/submit/public', body: { pictureId: '10' } }])
})

test('team admin has submission in space card menu and limited display sharing', async ({
  page,
}) => {
  const calls = await mockPublication(page, 'admin')
  await page.goto('/space/20')
  await page.locator('.picture-menu').click()
  await expect(page.getByRole('menuitem', { name: '投稿到公共图库' })).toBeVisible()
  await page.getByRole('menuitem', { name: '分享', exact: true }).click()
  await expect(page.getByText('拿到链接的人在有效期内都能看到这张图')).toBeVisible()
  await page.getByLabel('有效期', { exact: true }).selectOption('24')
  expect(calls).toHaveLength(0)
  await page.getByRole('button', { name: '生成临时直链' }).click()
  await expect(page.getByRole('textbox', { name: '临时直链', exact: true })).toHaveValue(
    'https://example.invalid/temporary-display',
  )
  expect(calls).toEqual([
    { path: '/api/picture/private/link', body: { pictureId: '10', validHours: 24 } },
  ])
  await page.getByRole('button', { name: 'Close', exact: true }).click()
  await page.locator('.picture-menu').click()
  await page.getByRole('menuitem', { name: '分享', exact: true }).click()
  await expect(page.getByRole('textbox', { name: '临时直链', exact: true })).toHaveCount(0)
})

for (const role of ['viewer', 'editor'] as const)
  test(`${role} cannot publish or generate a private link even as site admin`, async ({ page }) => {
    const calls = await mockPublication(page, role)
    await page.goto('/picture/10')
    await expect(page.getByRole('heading', { name: '空间作品' })).toBeVisible()
    await expect(page.getByRole('button', { name: '更多 ···' })).toHaveCount(0)
    await page.getByRole('button', { name: '分享', exact: true }).click()
    await expect(page.getByRole('button', { name: '生成临时直链' })).toHaveCount(0)
    expect(calls).toHaveLength(0)
  })
