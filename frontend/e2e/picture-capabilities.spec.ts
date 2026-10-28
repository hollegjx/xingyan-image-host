import { expect, test, type Page } from '@playwright/test'

async function disabledStorage(page: Page) {
  const calls: string[] = []
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname
    calls.push(path)
    let data: unknown = {}
    if (path === '/api/user/get/login') data = { id: '1', userName: '测试用户', userRole: 0 }
    if (path === '/api/picture/capabilities') data = { privateStorageConfigured: false, externalImageSearchEnabled: false }
    if (path === '/api/picture/get/vo') data = { id: '10', userId: '1', name: '公开图片', reviewStatus: 1, uploadStatus: 1 }
    if (path === '/api/space/get/vo') data = { id: '99', userId: '1', spaceName: '测试空间', spaceType: 0, permissionList: ['picture:view', 'picture:upload', 'picture:edit'], totalCount: 0 }
    if (path === '/api/picture/list/page/vo') data = { records: [], total: 0 }
    if (path === '/api/picture/tag_category') data = { tagList: [], categoryList: [] }
    await route.fulfill({ json: { code: 0, data } })
  })
  return calls
}
test('disabled search never sends picture to provider', async ({ page }) => {
  const calls = await disabledStorage(page)
  await page.goto('/picture/search?pictureId=10')
  await expect(page.getByText('外部以图搜图尚未启用', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '开始搜索' })).toHaveCount(0)
  expect(calls).not.toContain('/api/picture/search/picture')
})
test('direct private-upload route cannot bypass the disabled gate', async ({ page }) => {
  const calls = await disabledStorage(page)
  await page.goto('/picture/add?spaceId=99')
  await expect(page.getByText('私有图片存储尚未配置，暂不可上传或替换图片')).toBeVisible()
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  expect(calls).not.toContain('/api/picture/upload')
})
test('space stays navigable but its upload action is disabled', async ({ page }, info) => {
  await disabledStorage(page)
  await page.goto('/space/99')
  await expect(page.locator('#spaceDetailPage')).toHaveAttribute('aria-busy', 'false')
  await expect(page.getByRole('button', { name: '上传到此空间', exact: true })).toBeDisabled()
  await expect(page.getByText('私有图片存储尚未配置', { exact: false })).toBeVisible()
  await page.screenshot({ path: info.outputPath('private-storage-disabled.png'), animations: 'disabled' })
})
