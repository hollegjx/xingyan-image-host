import { expect, test, type Page } from '@playwright/test'

const sid = '9007199254740993'
const pid = '9007199254740995'
const uid = '9007199254740997'
const initialSpace = {
  id: sid,
  userId: uid,
  spaceName: '我的私人图库',
  spaceLevel: 0,
  spaceType: 0,
  canManageSpace: true,
  permissionList: [
    'picture:view',
    'picture:upload',
    'picture:edit',
    'picture:delete',
    'space:analyze',
  ],
  totalSize: '100',
  totalCount: '1',
  maxCount: '100',
  maxSize: '104857600',
}
const picture = {
  id: pid,
  userId: '7',
  spaceId: sid,
  name: '私人照片',
  url: `/api/picture/private/file?id=${pid}&variant=display`,
  thumbnailUrl: `/api/picture/private/file?id=${pid}&variant=thumbnail`,
  uploadStatus: 0,
}

async function mockSpace(
  page: Page,
  options: {
    owner?: boolean
    empty?: boolean
    failDetail?: boolean
    failUpload?: boolean
    paged?: boolean
    failFirstSearch?: boolean
  } = {},
) {
  let exists = !options.empty
  let records = [picture]
  let failedSearch = false
  const space = { ...initialSpace }
  if (options.owner === false) {
    space.permissionList = []
    space.canManageSpace = false
  }
  const requests: { path: string; body: Record<string, unknown>; url: URL }[] = []
  await page.route('**/api/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    if (path === '/api/picture/capabilities') return route.fulfill({ json: { code: 0, data: { privateStorageConfigured: true, externalImageSearchEnabled: true } } })
    const body = request.headers()['content-type']?.includes('application/json')
      ? request.postDataJSON()
      : {}
    requests.push({ path, body, url })
    let data: unknown = {}
    let code = 0
    let message = ''
    if (path === '/api/user/get/login')
      data = { id: options.owner === false ? '8' : uid, userName: '空间测试用户', userRole: 1 }
    else if (path === '/api/space/my/list/page/vo')
      data = { records: exists ? [space] : [], total: exists ? '1' : '0' }
    else if (path === '/api/space/list/level')
      data = [{ value: 0, text: '普通版', maxCount: '100', maxSize: '104857600' }]
    else if (path === '/api/space/add') {
      exists = true
      data = sid
    } else if (path === '/api/space/get/vo') data = space
    else if (path === '/api/space/edit') {
      space.spaceName = body.spaceName
      data = true
    } else if (path === '/api/picture/list/page/vo') {
      data = { records, total: options.paged ? '25' : String(records.length) }
      if (options.failFirstSearch && body.searchText && !failedSearch) {
        failedSearch = true
        code = 50000
        message = '图片查询暂时失败'
        data = null
      }
    } else if (path === '/api/picture/get/vo') {
      if (options.failDetail) {
        code = 40101
        message = '无权访问图片'
      } else data = picture
    } else if (path === '/api/picture/upload' || path === '/api/picture/upload/url') {
      if (options.failUpload) {
        code = 50001
        message = '空间容量不足'
      } else data = picture
    } else if (path === '/api/picture/edit') data = true
    else if (path === '/api/picture/delete') {
      records = []
      space.totalCount = '0'
      space.totalSize = '0'
      data = true
    } else if (path === '/api/picture/tag_category') data = { categoryList: [], tagList: [] }
    else if (path === '/api/picture/private/file') {
      await route.fulfill({
        contentType: 'image/svg+xml',
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="teal"/></svg>',
      })
      return
    }
    await route.fulfill({ json: { code, data, message } })
  })
  return requests
}

test('create space keeps Long ID and scopes list; mobile layout and delete refresh usage', async ({
  page,
}, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const requests = await mockSpace(page, { empty: true })
  await page.goto('/space/mine')
  await page.getByPlaceholder('默认空间').fill('私人图库')
  await page.getByRole('button', { name: '创建空间', exact: true }).click()
  await expect(page).toHaveURL(`/space/${sid}`)
  await expect(page.getByText('私人照片', { exact: true })).toBeVisible()
  expect(requests.find((r) => r.path === '/api/picture/list/page/vo')?.body.spaceId).toBe(sid)
  await expect(page.locator('.picture-thumbnail img')).toHaveAttribute('src', picture.thumbnailUrl)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await page.screenshot({
    path: testInfo.outputPath('private-space-mobile.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await page.getByRole('button', { name: '图片操作 私人照片' }).click()
  await page.getByRole('menuitem', { name: /删除$/ }).click()
  await page.locator('.ant-modal-confirm .ant-btn-primary').click()
  await expect(page.getByText('这里，留给你的下一份灵感。')).toBeVisible()
  expect(requests.find((r) => r.path === '/api/picture/delete')?.body.id).toBe(pid)
})

test('admin metadata access does not allow another owner private gallery or upload', async ({
  page,
}) => {
  const requests = await mockSpace(page, { owner: false })
  await page.goto(`/space/${sid}`)
  await expect(page.getByText('没有此空间的查看权限')).toBeVisible()
  expect(requests.some((r) => r.path === '/api/picture/list/page/vo')).toBe(false)
  await page.goto(`/picture/add?spaceId=${sid}`)
  await expect(page.getByText('没有此空间的上传或编辑权限')).toBeVisible()
  await expect(page.locator('input[type=file]')).toHaveCount(0)
})

test('invalid space parameters and failed edit lookup never fall back to public upload', async ({
  page,
}) => {
  await mockSpace(page, { failDetail: true })
  for (const query of ['spaceId=0', 'spaceId=1&spaceId=2', `id=${pid}`]) {
    await page.goto(`/picture/add?${query}`)
    await expect(page.locator('#addPicturePage .ant-alert-error')).toBeVisible()
    await expect(page.locator('input[type=file]')).toHaveCount(0)
  }
})

test('file upload and URL replacement preserve space and picture IDs, then save to space', async ({
  page,
}) => {
  const requests = await mockSpace(page)
  await page.goto(`/picture/add?spaceId=${sid}`)
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'photo.png', mimeType: 'image/png', buffer: Buffer.from('fixture') })
  await expect(page).toHaveURL(`/picture/add?id=${pid}&spaceId=${sid}`)
  const upload = requests.find((r) => r.path === '/api/picture/upload')
  expect(upload?.url.searchParams.get('spaceId')).toBe(sid)
  expect(upload?.url.searchParams.has('id')).toBe(false)
  await page.getByRole('tab', { name: 'URL 上传' }).click()
  await page.getByPlaceholder('请输入图片 URL').fill('https://example.com/image.png')
  await page.getByRole('button', { name: /^提\s*交$/ }).click()
  await expect
    .poll(() => requests.filter((r) => r.path === '/api/picture/upload/url').length)
    .toBe(1)
  expect(requests.find((r) => r.path === '/api/picture/upload/url')?.body).toMatchObject({
    id: pid,
    spaceId: sid,
  })
  await page.getByRole('button', { name: '保存图片信息' }).click()
  await expect(page).toHaveURL(`/space/${sid}`)
  expect(requests.find((r) => r.path === '/api/picture/edit')?.body.id).toBe(pid)
})

test('quota failure keeps existing picture and edit ownership comes from space', async ({
  page,
}) => {
  const requests = await mockSpace(page, { failUpload: true })
  await page.goto(`/picture/${pid}`)
  await expect(page.getByRole('button', { name: /编\s*辑$/ })).toBeVisible()
  await page.getByRole('button', { name: /编\s*辑$/ }).click()
  await page.getByRole('tab', { name: 'URL 上传' }).click()
  await page.getByPlaceholder('请输入图片 URL').fill('https://example.com/too-large.png')
  await page.getByRole('button', { name: /^提\s*交$/ }).click()
  await expect(page.getByText('图片导入失败，空间容量不足')).toBeVisible()
  await expect(page.locator('.uploaded-preview')).toHaveAttribute('src', picture.url)
  expect(requests.find((r) => r.path === '/api/picture/get/vo')?.url.searchParams.get('id')).toBe(
    pid,
  )
})

test('space search submits combined filters, preserves them across pagination and resets them', async ({
  page,
}) => {
  const requests = await mockSpace(page, { paged: true })
  await page.goto(`/space/${sid}`)
  await page.getByPlaceholder('搜索空间内图片').fill('日落')
  await page.getByRole('button', { name: '筛选', exact: true }).click()
  await page.getByRole('combobox', { name: '分类', exact: true }).fill('风景')
  const tags = page.getByRole('combobox', { name: '标签（同时包含）', exact: true })
  await tags.fill('旅行')
  await tags.press('Enter')
  await tags.press('Escape')
  await page.getByText('详细筛选', { exact: true }).click()
  await page.getByPlaceholder('按名称筛选').fill('海边')
  await page.getByPlaceholder('按简介筛选').fill('傍晚')
  await page.getByPlaceholder('例如 1920').fill('1920')
  await page.getByPlaceholder('例如 1080').fill('1080')
  await page.getByPlaceholder('开始日期').click()
  await page.getByPlaceholder('开始日期').fill('2026-10-01')
  await page.getByPlaceholder('开始日期').press('Enter')
  await page.getByPlaceholder('结束日期').click()
  await page.getByPlaceholder('结束日期').fill('2026-10-02')
  await page.getByPlaceholder('结束日期').press('Enter')
  await page.locator('.picture-search-form .ant-select').last().click()
  await page.getByTitle('PNG', { exact: true }).click()
  const submit = page.getByRole('button', { name: /^搜\s*索$/ })
  await submit.click()
  const expected = {
    spaceId: sid,
    current: 1,
    pageSize: 12,
    searchText: '日落',
    category: '风景',
    tags: ['旅行'],
    name: '海边',
    introduction: '傍晚',
    picWidth: 1920,
    picHeight: 1080,
    picFormat: 'png',
    startEditTime: '2026-09-30T16:00:00.000Z',
    endEditTime: '2026-10-02T16:00:00.000Z',
  }
  const lastQuery = () =>
    requests.filter((r) => r.path === '/api/picture/list/page/vo').at(-1)?.body
  await expect.poll(lastQuery).toMatchObject(expected)
  await page.locator('.ant-pagination-item-2').click()
  await expect.poll(lastQuery).toMatchObject({ ...expected, current: 2 })
  await expect(page.getByPlaceholder('搜索空间内图片')).toHaveValue('日落')
  await expect(page.getByPlaceholder('开始日期')).toHaveValue('2026-10-01')
  await page.getByRole('button', { name: /^重\s*置$/ }).click()
  await expect
    .poll(lastQuery)
    .toEqual({ spaceId: sid, current: 1, pageSize: 12, sortField: 'createTime', sortOrder: 'desc' })
  await expect(page.getByPlaceholder('例如 1920')).toHaveValue('')
  await expect(page.getByPlaceholder('开始日期')).toHaveValue('')
})

test('expanded search and recent dates fit a mobile screen', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 390, height: 844 })
  const requests = await mockSpace(page)
  await page.goto(`/space/${sid}`)
  await page.getByRole('button', { name: '筛选', exact: true }).click()
  await page.getByText('详细筛选', { exact: true }).click()
  await page.getByRole('button', { name: '近 7 天' }).click()
  await page.getByRole('button', { name: /^搜\s*索$/ }).click()
  await expect
    .poll(
      () =>
        requests.filter((r) => r.path === '/api/picture/list/page/vo').at(-1)?.body.startEditTime,
    )
    .toBeTruthy()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await page.screenshot({
    path: testInfo.outputPath('space-search-mobile.png'),
    fullPage: true,
    animations: 'disabled',
  })
})

test('a failed search hides old results and retry preserves the form and applied filters', async ({
  page,
}) => {
  const requests = await mockSpace(page, { failFirstSearch: true })
  await page.goto(`/space/${sid}`)
  await expect(page.getByText('私人照片', { exact: true })).toBeVisible()
  await page.getByPlaceholder('搜索空间内图片').fill('日落')
  await page.getByRole('button', { name: /^搜\s*索$/ }).click()
  await expect(page.getByText('图片查询暂时失败', { exact: true })).toBeVisible()
  await expect(page.getByText('私人照片', { exact: true })).toHaveCount(0)
  await expect(page.getByPlaceholder('搜索空间内图片')).toBeHidden()
  await page.getByRole('button', { name: /^重\s*试$/ }).click()
  await expect(page.getByText('私人照片', { exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('搜索空间内图片')).toHaveValue('日落')
  expect(requests.filter((r) => r.path === '/api/picture/list/page/vo').at(-1)?.body).toMatchObject(
    {
      spaceId: sid,
      searchText: '日落',
      current: 1,
    },
  )
})
