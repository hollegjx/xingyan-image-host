import { test, expect, type Page } from '@playwright/test'

async function setup(page: Page, admin = false) {
  let records = [
    {
      id: '101',
      userId: '1',
      name: '待审核测试作品',
      category: '',
      url: '/favicon.ico',
      uploadStatus: 1,
      reviewStatus: 0,
    },
    {
      id: '102',
      userId: '1',
      name: '已公开测试作品',
      url: '/favicon.ico',
      uploadStatus: 1,
      reviewStatus: 1,
      reviewerId: '999',
      reviewerName: '审核员小林',
    },
  ]
  const calls: { path: string; body: Record<string, unknown> }[] = []
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const body =
      route.request().method() === 'POST' &&
      route.request().headers()['content-type']?.includes('application/json')
        ? route.request().postDataJSON()
        : {}
    let data: unknown = {}
    if (path === '/api/user/get/login')
      data = { id: '1', userName: '本人', userRole: admin ? 1 : 0 }
    if (path === '/api/picture/capabilities') data = { privateStorageConfigured: true }
    if (path === '/api/picture/tag_category') data = { categoryList: ['风景'], tagList: ['自然'] }
    if (path === '/api/picture/get/vo') data = records[0]
    if (path === '/api/picture/my/list/page/vo') data = { records, total: String(records.length) }
    if (path === '/api/picture/list/page/vo') data = { records: [], total: '0' }
    if (path === '/api/picture/list/page/review') {
      calls.push({ path, body })
      const rows = body.allReviewStatuses
        ? records
        : records.filter((p) => p.reviewStatus === (body.reviewStatus ?? 0))
      data = { records: rows, total: String(rows.length) }
    }
    if (path === '/api/picture/submission/withdraw' || path === '/api/picture/submission/delete') {
      calls.push({ path, body })
      records = path.endsWith('/delete')
        ? records.filter((p) => p.id !== body.pictureId)
        : records.map((p) => (p.id === body.pictureId ? { ...p, reviewStatus: 3 } : p))
      data = true
    }
    if (path === '/api/picture/upload') data = { ...records[0], uploadStatus: 0 }
    await route.fulfill({ json: { code: 0, data } })
  })
  return calls
}

for (const action of ['撤回', '删除投稿'] as const) {
  test(`owner ${action} confirms and refreshes pending submissions`, async ({ page }) => {
    const calls = await setup(page)
    await page.goto('/picture/mine')
    const pending = page.getByRole('article', { name: '待审核测试作品', exact: true })
    const published = page.getByRole('article', { name: '已公开测试作品', exact: true })
    await expect(published.getByRole('button', { name: action, exact: true })).toHaveCount(0)
    await pending.getByRole('button', { name: action, exact: true }).click()
    expect(calls).toHaveLength(0)
    await page
      .getByRole('dialog')
      .getByRole('button', { name: action === '撤回' ? '撤回' : '删除', exact: true })
      .click()
    await expect.poll(() => calls.length).toBe(1)
    expect(calls[0]).toEqual({
      path: `/api/picture/submission/${action === '撤回' ? 'withdraw' : 'delete'}`,
      body: { pictureId: '101' },
    })
    if (action === '撤回') {
      await expect(pending.locator('.submission-status')).toHaveText('已撤回')
      await expect(pending.getByRole('button', { name: '撤回', exact: true })).toHaveCount(0)
    } else await expect(pending).toHaveCount(0)
    await expect(published).toBeVisible()
  })
}

test('admin all is explicit, reviewer is named, and table fits desktop and mobile', async ({
  page,
}, info) => {
  const calls = await setup(page, true)
  await page.goto('/admin/pictureManage')
  await expect(page.getByText('待审核测试作品', { exact: true })).toBeVisible()
  expect(calls[0]?.body).toMatchObject({ reviewStatus: 0, allReviewStatuses: false })
  await page.getByText('全部', { exact: true }).click()
  await expect(page.getByText('已公开测试作品', { exact: true })).toBeVisible()
  expect(calls.at(-1)?.body).toMatchObject({ allReviewStatuses: true })
  expect(calls.at(-1)?.body.reviewStatus).toBeUndefined()
  await expect(page.getByText(/审核人 审核员小林/)).toBeVisible()
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 })
    expect(
      await page
        .locator('.ant-table-content')
        .evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
    ).toBe(true)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBe(
      true,
    )
    await page.screenshot({ path: info.outputPath(`admin-table-${width}.png`), fullPage: true })
  }
})

test('uploaded draft collapses dropzone and exposes metadata; pending detail is labeled', async ({
  page,
}, info) => {
  await setup(page)
  await page.goto('/picture/101')
  await expect(page.getByText('审核中', { exact: true })).toBeVisible()
  await expect(page.getByText('未分类', { exact: true })).toBeVisible()
  await page.goto('/picture/add')
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'test.png', mimeType: 'image/png', buffer: Buffer.from('mock image') })
  await expect(page.getByRole('button', { name: '替换图片', exact: true })).toBeVisible()
  await expect(page.locator('.ant-upload-drag')).toHaveCount(0)
  await expect(page.getByPlaceholder('请输入名称', { exact: true })).toBeVisible()
  await page.screenshot({ path: info.outputPath('draft-metadata.png'), fullPage: true })
})
