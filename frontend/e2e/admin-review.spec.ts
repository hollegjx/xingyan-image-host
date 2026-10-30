import { test, expect } from '@playwright/test'

const passed = [{ id: '1', name: '已通过的图片', url: '/fixtures/a.svg', reviewStatus: 1 }]

test('admin review list switches status immediately and explains an empty queue', async ({
  page,
}) => {
  const statuses: unknown[] = []
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = {}
    if (path === '/api/user/get/login') data = { id: '1', userName: '测试管理员', userRole: 1 }
    else if (path === '/api/picture/tag_category') data = { categoryList: [], tagList: [] }
    else if (path === '/api/picture/list/page/review') {
      // Mirrors the backend: a missing status means "pending".
      const status = route.request().postDataJSON().reviewStatus ?? 0
      statuses.push(status)
      const records = status === 1 ? passed : []
      data = { records, total: records.length }
    }
    await route.fulfill({ json: { code: 0, data } })
  })
  await page.route('**/fixtures/**', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg"/>',
    }),
  )
  await page.goto('/admin/pictureManage')
  await expect(page.getByText('没有待审核的投稿')).toBeVisible()
  expect(statuses).toEqual([0])
  await page.getByRole('button', { name: '查看已通过的图片' }).click()
  await expect(page.getByRole('cell', { name: '已通过的图片', exact: true })).toBeVisible()
  await page.getByText('已拒绝', { exact: true }).click()
  await expect(page.getByText('没有已拒绝的图片')).toBeVisible()
  expect(statuses).toEqual([0, 1, 2])
})
