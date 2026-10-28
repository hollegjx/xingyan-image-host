import { expect, test, type Page } from '@playwright/test'

const spaceId = '9007199254740997'
const otherSpace = '9007199254740998'
const rows = [
  {
    id: '9007199254741001',
    spaceId,
    userId: '1',
    name: '猫图',
    url: '/fixture.svg',
    picColor: '#ff0000',
    uploadStatus: 1,
    reviewStatus: 1,
  },
  {
    id: '9007199254741002',
    spaceId,
    userId: '1',
    name: '狗图',
    url: '/fixture.svg',
    picColor: '#0000ff',
    uploadStatus: 1,
    reviewStatus: 1,
  },
]

async function setup(
  page: Page,
  options: { batchFail?: boolean; colorFail?: boolean; forbidden?: boolean } = {},
) {
  const calls: { path: string; body: Record<string, unknown> }[] = []
  await page.route('**/fixture.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80"><rect width="120" height="80" fill="#ddd"/></svg>',
    }),
  )
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/picture/capabilities') return route.fulfill({ json: { code: 0, data: { privateStorageConfigured: true, externalImageSearchEnabled: true } } })
    const body = route.request().postData() ? route.request().postDataJSON() : {}
    calls.push({ path, body })
    let data: unknown = {}
    let code = 0
    let message = ''
    if (path === '/api/user/get/login') data = { id: '1', userRole: 0, userName: '空间用户' }
    else if (path === '/api/space/get/vo') {
      const id = new URL(route.request().url()).searchParams.get('id')
      data = {
        id,
        userId: options.forbidden ? '2' : '1',
        spaceType: 0,
        canManageSpace: !options.forbidden,
        permissionList: options.forbidden
          ? []
          : ['picture:view', 'picture:upload', 'picture:edit', 'picture:delete', 'space:analyze'],
        spaceName: id === spaceId ? '私人测试空间' : '第二空间',
        maxCount: 100,
        totalCount: 2,
      }
    } else if (path === '/api/picture/tag_category')
      data = { categoryList: ['旅行'], tagList: ['测试'] }
    else if (path === '/api/picture/list/page/vo')
      data = { records: rows.map((row) => ({ ...row, spaceId: body.spaceId })), total: 24 }
    else if (path === '/api/picture/search/color') {
      data = [rows[0]]
      if (options.colorFail) {
        code = 50000
        message = '颜色搜索失败'
        data = null
      }
    } else if (path === '/api/picture/edit/batch') {
      data = (body.pictureIdList as string[]).length
      if (options.batchFail) {
        code = 40101
        message = '所选图片已删除或不属于当前空间，请刷新后重试'
        data = null
      }
    } else if (path === '/api/picture/get/vo') data = rows[0]
    await route.fulfill({ json: { code, data, message } })
  })
  return calls
}

test('color search is independent, uses exact space ID, hides paging and returns to filters', async ({
  page,
}) => {
  const calls = await setup(page)
  await page.goto(`/space/${spaceId}`)
  await expect(page.getByText('猫图', { exact: true }).first()).toBeVisible()
  await page.getByRole('button', { name: '颜色搜索', exact: true }).click()
  await page.getByLabel('选择颜色').fill('#ff0000')
  await page.getByRole('button', { name: '搜索此颜色' }).click()
  await expect(page.getByText('颜色搜索独立于上方属性条件', { exact: false })).toBeVisible()
  await expect(page.locator('.space-picture-card')).toHaveCount(1)
  await expect(page.locator('.ant-pagination')).toHaveCount(0)
  expect(calls.find((call) => call.path === '/api/picture/search/color')?.body).toEqual({
    spaceId,
    picColor: '#ff0000',
  })
  await page.getByRole('button', { name: '返回属性搜索' }).click()
  await expect(page.locator('.space-picture-card')).toHaveCount(2)
  await expect(page.locator('.ant-pagination')).toBeVisible()
})

test('private share uses a canonical detail link, warns, renders QR and fits mobile', async ({
  page,
}, info) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`/space/${spaceId}`)
  await page.getByRole('button', { name: '图片操作 猫图' }).click()
  await page.getByRole('menuitem', { name: /分享$/ }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('私人图片链接不会授予访问权限')).toBeVisible()
  await expect(dialog.locator('#picture-share-link')).toHaveValue(
    new URL(`/picture/${rows[0]!.id}`, page.url()).href,
  )
  await expect(dialog.locator('.ant-qrcode')).toBeVisible()
  await expect(dialog.locator('.ant-qrcode canvas, .ant-qrcode svg')).toHaveCount(1)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('private-share-mobile.png'), fullPage: true })
})

test('batch preview and ordered current-page selection submit no unchecked metadata', async ({
  page,
}, info) => {
  const calls = await setup(page)
  await page.goto(`/space/${spaceId}`)
  await page.getByRole('button', { name: '选择图片', exact: true }).click()
  await page.getByRole('checkbox', { name: '全选当前页' }).check()
  await page.getByRole('button', { name: '批量编辑（2）' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('checkbox', { name: '批量重命名' }).check()
  await dialog.getByLabel('命名规则').fill('旅行_{序号}')
  await expect(dialog.getByLabel('重命名预览')).toContainText('猫图 → 旅行_1')
  await expect(dialog.getByLabel('重命名预览')).toContainText('狗图 → 旅行_2')
  await page.screenshot({ path: info.outputPath('batch-preview.png'), fullPage: true })
  await dialog.getByRole('button', { name: '确认修改' }).click()
  await expect(dialog).toHaveCount(0)
  expect(calls.find((call) => call.path === '/api/picture/edit/batch')?.body).toEqual({
    spaceId,
    pictureIdList: rows.map((row) => row.id),
    nameRule: '旅行_{序号}',
  })
  await expect(page.getByRole('button', { name: '批量编辑（0）' })).toBeDisabled()
})

test('batch failure keeps the dialog and preview; explicit clearing is preserved', async ({
  page,
}) => {
  const calls = await setup(page, { batchFail: true })
  await page.goto(`/space/${spaceId}`)
  await page.getByRole('button', { name: '选择图片', exact: true }).click()
  await page.getByRole('checkbox', { name: '选择 猫图' }).check()
  await page.getByRole('button', { name: '批量编辑（1）' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('checkbox', { name: '修改分类' }).check()
  await dialog.getByRole('checkbox', { name: '替换标签' }).check()
  await dialog.getByRole('button', { name: '确认修改' }).click()
  await expect(dialog.getByText('所选图片已删除或不属于当前空间，请刷新后重试')).toBeVisible()
  expect(calls.find((call) => call.path === '/api/picture/edit/batch')?.body).toEqual({
    spaceId,
    pictureIdList: [rows[0]!.id],
    category: '',
    tags: [],
  })
})

test('pagination clears selection and unauthorized visitors cannot load private pictures', async ({
  page,
}) => {
  await setup(page)
  await page.goto(`/space/${spaceId}`)
  await page.getByRole('button', { name: '选择图片', exact: true }).click()
  await page.getByRole('checkbox', { name: '全选当前页' }).check()
  await page.locator('.ant-pagination-item-2').click()
  await expect(page.getByRole('button', { name: '批量编辑（0）' })).toBeDisabled()
  // End the authorized document before switching fixtures: an in-flight page-2 request
  // must not be counted as a new request from the subsequently unauthorized document.
  await page.goto('about:blank')
  await page.unroute('**/api/**')
  const calls = await setup(page, { forbidden: true })
  await page.goto(`/space/${spaceId}`)
  await expect(page.getByText('没有此空间的查看权限')).toBeVisible()
  expect(calls.some((call) => call.path === '/api/picture/list/page/vo')).toBe(false)
  expect(calls.some((call) => call.path === '/api/picture/search/color')).toBe(false)
})

test('color errors clear previous results instead of presenting an empty success', async ({
  page,
}) => {
  await setup(page, { colorFail: true })
  await page.goto(`/space/${spaceId}`)
  await expect(page.locator('.space-picture-card')).toHaveCount(2)
  await page.getByRole('button', { name: '颜色搜索', exact: true }).click()
  await page.getByRole('button', { name: '搜索此颜色' }).click()
  await expect(page.getByText('颜色搜索失败', { exact: true })).toBeVisible()
  await expect(page.locator('.space-picture-card')).toHaveCount(0)
  await page.getByRole('button', { name: '返回属性搜索' }).click()
  await expect(page.locator('.space-picture-card')).toHaveCount(2)
})

test('detail displays primary color and shares without including incoming query parameters', async ({
  page,
}) => {
  await setup(page)
  await page.goto(`/picture/${rows[0]!.id}?token=should-not-copy`)
  await expect(page.getByText('#ff0000', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '分享', exact: true }).click()
  await expect(page.locator('#picture-share-link')).not.toHaveValue(/token=/)
})

test('late color response cannot overwrite a newly selected space', async ({ page }) => {
  await setup(page)
  let release!: () => void
  let started!: () => void
  const entered = new Promise<void>((resolve) => {
    started = resolve
  })
  const wait = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/api/picture/search/color', async (route) => {
    started()
    await wait
    await route.fulfill({ json: { code: 0, data: [{ ...rows[0], name: '旧颜色结果' }] } })
  })
  await page.goto(`/space/${spaceId}`)
  await page.getByRole('button', { name: '颜色搜索', exact: true }).click()
  await page.getByRole('button', { name: '搜索此颜色' }).click()
  await entered
  // SPA navigation via history and popstate exercises reuse of the same route component.
  await page.evaluate((id) => {
    history.pushState({}, '', `/space/${id}`)
    dispatchEvent(new PopStateEvent('popstate'))
  }, otherSpace)
  await expect(page.getByRole('heading', { name: /第二空间/ })).toBeVisible()
  release()
  await expect(page.getByText('旧颜色结果')).toHaveCount(0)
  await expect(page.locator('.space-picture-card')).toHaveCount(2)
})
