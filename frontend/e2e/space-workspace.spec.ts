import { expect, test, type Page } from '@playwright/test'
import { coverSvg } from '../dev/design-preview.js'

const sid = '9007199254740993'
const rows = Array.from({ length: 6 }, (_, index) => ({
  id: String(900100 + index),
  spaceId: sid,
  userId: '1',
  name: ['山野之间', '光与拱门', '向上生长', '少即是多', '海岸漫游', '形状实验'][index],
  url: `/workspace-${index}.svg`,
  thumbnailUrl: `/workspace-${index}.svg`,
  category: '灵感',
  picFormat: 'png',
  uploadStatus: 1,
}))

async function setup(page: Page, options: { multiple?: boolean; failure?: boolean } = {}) {
  const calls: { path: string; body: Record<string, unknown> }[] = []
  const space = {
    id: sid,
    userId: '1',
    spaceName: '我的灵感库',
    spaceType: 0,
    spaceLevel: 0,
    canManageSpace: true,
    totalCount: 6,
    totalSize: 24576,
    maxCount: 100,
    maxSize: 104857600,
    permissionList: [
      'picture:view',
      'picture:upload',
      'picture:edit',
      'picture:delete',
      'space:analyze',
    ],
  }
  await page.route('**/workspace-*.svg', (route) =>
    route.fulfill({
      contentType: 'image/svg+xml',
      body: coverSvg(Number(/workspace-(\d+)/.exec(route.request().url())?.[1] || 0)),
    }),
  )
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    const path = url.pathname
    if (path === '/api/picture/capabilities') return route.fulfill({ json: { code: 0, data: { privateStorageConfigured: true, externalImageSearchEnabled: true } } })
    const body = route.request().headers()['content-type']?.includes('application/json')
      ? route.request().postDataJSON()
      : {}
    calls.push({ path, body })
    let data: unknown = {},
      code = 0,
      message = ''
    if (path === '/api/user/get/login')
      data = { id: '1', userRole: options.multiple ? 1 : 0, userName: '设计示例' }
    else if (path === '/api/space/my/list/page/vo') {
      data = {
        records: options.multiple
          ? [space, { ...space, id: '9007199254740995', spaceName: '旅行记忆' }]
          : [space],
        total: options.multiple ? 2 : 1,
      }
      if (options.failure) {
        code = 50000
        data = null
        message = '空间列表暂不可用'
      }
    } else if (path === '/api/space/get/vo') data = space
    else if (path === '/api/picture/list/page/vo') data = { records: rows, total: 6 }
    else if (path === '/api/picture/tag_category')
      data = { categoryList: ['自然'], tagList: ['旅行'] }
    else if (path === '/api/space/list/level')
      data = [{ value: 0, text: '普通版', maxCount: 100, maxSize: 104857600 }]
    else if (path === '/api/space/edit') {
      space.spaceName = String(body.spaceName)
      data = true
    }
    await route.fulfill({ json: { code, data, message } })
  })
  return calls
}

test('single private space opens directly with images first and contextual controls', async ({
  page,
}, info) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  const calls = await setup(page)
  await page.goto('/space/mine')
  await expect(page).toHaveURL(`/space/${sid}`)
  await expect(page.locator('.space-picture-card')).toHaveCount(6)
  expect(calls.find((call) => call.path === '/api/picture/list/page/vo')?.body.spaceId).toBe(sid)
  await expect(page.getByRole('button', { name: '进入空间', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '重命名空间' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /批量编辑/ })).toHaveCount(0)
  await expect(page.getByRole('combobox', { name: '分类', exact: true })).toBeHidden()
  await expect(page.getByLabel('选择颜色')).toHaveCount(0)
  await expect(page.getByRole('link', { name: '创建图片' })).toHaveCount(0)
  expect((await page.locator('.space-picture-cover').first().boundingBox())!.y).toBeLessThan(520)
  await expect(page.locator('#spaceDetailPage .ant-spin-container')).toHaveCSS('opacity', '1')
  await page.screenshot({
    path: info.outputPath('space-workspace-desktop.png'),
    animations: 'disabled',
  })

  await page.getByRole('button', { name: '选择图片', exact: true }).click()
  await page.getByRole('checkbox', { name: '选择 山野之间' }).check()
  await expect(page.getByRole('button', { name: '批量编辑（1）' })).toBeEnabled()
  await page.getByRole('button', { name: '完成选择' }).click()
  await expect(page.getByRole('region', { name: '批量操作' })).toHaveCount(0)
  await page.getByRole('button', { name: '选择图片', exact: true }).click()
  await expect(page.getByRole('button', { name: '批量编辑（0）' })).toBeDisabled()
  await page.getByRole('button', { name: '完成选择' }).click()
  await page.getByRole('button', { name: '上传到此空间' }).click()
  await expect(page).toHaveURL(`/picture/add?spaceId=${sid}`)
})

test('space settings contain usage and dangerous actions; rename preserves the exact ID', async ({
  page,
}, info) => {
  const calls = await setup(page)
  await page.goto(`/space/${sid}`)
  await page.getByRole('button', { name: '空间设置' }).click()
  const drawer = page.getByRole('region', { name: '空间设置' })
  await expect(drawer.getByText('图片数量：6 / 100 张')).toBeVisible()
  await expect(drawer.getByRole('button', { name: '删除空空间' })).toBeVisible()
  await page.screenshot({
    path: info.outputPath('space-settings-desktop.png'),
    animations: 'disabled',
  })
  await drawer.getByRole('button', { name: '重命名空间' }).click()
  await page.getByPlaceholder('空间名称', { exact: true }).fill('新的灵感库')
  await page.locator('.ant-modal .ant-btn-primary').click()
  await expect(page.getByRole('heading', { name: '新的灵感库', level: 2 })).toBeVisible()
  expect(calls.find((call) => call.path === '/api/space/edit')?.body).toEqual({
    id: sid,
    spaceName: '新的灵感库',
  })
  await drawer.getByRole('button', { name: '查看空间列表' }).click()
  await expect(page).toHaveURL('/space/mine?view=list')
  await expect(page.getByRole('link', { name: /新的灵感库/ })).toBeVisible()
})

test('multiple spaces and an explicit overview never auto-select a space', async ({ page }) => {
  await setup(page, { multiple: true })
  await page.goto('/space/mine')
  await expect(page.getByRole('link', { name: /旅行记忆/ })).toBeVisible()
  await expect(page).toHaveURL('/space/mine')
  await page.unroute('**/api/**')
  await setup(page)
  await page.goto('/space/mine?view=list')
  await expect(page.getByRole('link', { name: /我的灵感库/ })).toBeVisible()
  await expect(page).toHaveURL('/space/mine?view=list')
  await page.getByRole('link', { name: /我的灵感库/ }).click()
  await expect(page).toHaveURL(`/space/${sid}`)
})

test('list failure never redirects or starts a picture query', async ({ page }) => {
  const calls = await setup(page, { failure: true })
  await page.goto('/space/mine')
  await expect(page.getByText('空间列表暂不可用')).toBeVisible()
  await expect(page).toHaveURL('/space/mine')
  expect(calls.some((call) => call.path === '/api/picture/list/page/vo')).toBe(false)
})

test('mobile workspace exposes pictures in the first screen and keeps settings and filters usable', async ({
  page,
}, info) => {
  await setup(page)
  for (const width of [320, 390, 768]) {
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/space/mine')
    await expect(page.locator('.space-picture-card')).toHaveCount(6)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    expect((await page.locator('.space-picture-cover').first().boundingBox())!.y).toBeLessThan(650)
  }
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('#spaceDetailPage .ant-spin-container')).toHaveCSS('opacity', '1')
  await page.screenshot({
    path: info.outputPath('space-workspace-mobile.png'),
    animations: 'disabled',
  })
  await page.getByRole('button', { name: '筛选', exact: true }).click()
  await expect(page.getByRole('combobox', { name: '分类', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '筛选', exact: true }).click()
  await page.getByRole('button', { name: '空间设置' }).click()
  await expect(page.getByRole('button', { name: '删除空空间' })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({
    path: info.outputPath('space-settings-mobile.png'),
    animations: 'disabled',
  })
})
