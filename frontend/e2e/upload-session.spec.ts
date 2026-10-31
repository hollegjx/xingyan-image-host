import { test, expect, type Page } from '@playwright/test'

async function setup(page: Page) {
  let uploads = 0
  let expired = true
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = {}
    if (path === '/api/user/get/login') data = { id: '1', userName: '回归用户', userRole: 0 }
    if (path === '/api/user/login') expired = false
    if (path === '/api/picture/tag_category') data = { tagList: [], categoryList: [] }
    if (path === '/api/picture/capabilities') data = { externalImageSearchEnabled: false }
    if (path === '/api/picture/get/vo') data = { id: '10', userId: '2', name: '测试图片', url: '/favicon.ico', uploadStatus: 1, reviewStatus: 1, favoriteCount: 0, favorited: false }
    if (path === '/api/picture/favorite/set' && expired) return route.fulfill({ json: { code: 40100, message: '未登录' } })
    if (path === '/api/picture/upload') {
      uploads++
      data = { id: '20', name: '单张图片', url: '/favicon.ico', uploadStatus: 0 }
    }
    await route.fulfill({ json: { code: 0, data } })
  })
  return () => uploads
}

test('multi-file drop explains that nothing uploaded, then single-file upload still works', async ({ page }) => {
  const uploads = await setup(page)
  await page.goto('/picture/add')
  const dropzone = page.locator('.ant-upload-drag')
  await expect(dropzone).toBeVisible()
  const files = await page.evaluateHandle(() => {
    const transfer = new DataTransfer()
    transfer.items.add(new File(['first'], 'first.png', { type: 'image/png' }))
    transfer.items.add(new File(['second'], 'second.png', { type: 'image/png' }))
    return transfer
  })
  await dropzone.dispatchEvent('drop', { dataTransfer: files })
  await expect(page.getByText('一次只能上传一张，本次拖入了 2 个文件，均未上传，请重新选择')).toBeVisible()
  expect(uploads()).toBe(0)
  await page.locator('input[type=file]').setInputFiles({ name: 'single.png', mimeType: 'image/png', buffer: Buffer.from('single') })
  await expect(page.getByText('图片已上传为草稿，请完善信息后提交')).toBeVisible()
  expect(uploads()).toBe(1)
  await files.dispose()
})

test('expired session removes user menu and returns to the original page after login', async ({ page }) => {
  await setup(page)
  await page.goto('/picture/10?from=gallery')
  await expect(page.getByRole('button', { name: '用户菜单' })).toBeVisible()
  await page.getByRole('button', { name: '收藏 · 0', exact: true }).click()
  await expect(page.getByText('登录已过期，请重新登录', { exact: true })).toBeVisible()
  await expect(page).toHaveURL(/\/user\/login\?redirect=/)
  await expect(page.getByRole('button', { name: '用户菜单' })).toHaveCount(0)
  await page.locator('input').first().fill('regression-user')
  await page.locator('input[type=password]').fill('test-password-only')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/picture\/10\?from=gallery$/)
  await expect(page.getByRole('button', { name: '用户菜单' })).toBeVisible()
})
