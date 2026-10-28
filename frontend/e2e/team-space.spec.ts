import { expect, test, type Page } from '@playwright/test'

const sid = '9007199254740997',
  uid = '9007199254740999',
  pid = '9007199254741003'
const policy: Record<string, string[]> = {
  viewer: ['picture:view'],
  editor: ['picture:view', 'picture:upload', 'picture:edit', 'picture:delete'],
  admin: [
    'picture:view',
    'picture:upload',
    'picture:edit',
    'picture:delete',
    'spaceUser:manage',
    'space:analyze',
  ],
}
async function mockTeam(page: Page, role = 'admin', failAdd = false) {
  const calls: { path: string; body: Record<string, unknown> }[] = []
  const space = {
    id: sid,
    userId: '2',
    spaceName: '设计团队',
    spaceType: 1,
    spaceLevel: 0,
    canManageSpace: false,
    permissionList: policy[role],
    totalCount: '1',
    totalSize: '100',
    maxCount: '100',
    maxSize: '104857600',
  }
  let members = [
    {
      id: '201',
      userId: '2',
      userName: '创建者',
      spaceId: sid,
      spaceRole: 'admin',
      owner: true,
      space,
    },
    {
      id: '202',
      userId: uid,
      userName: '当前成员',
      spaceId: sid,
      spaceRole: role,
      owner: false,
      space,
    },
  ]
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/picture/capabilities') return route.fulfill({ json: { code: 0, data: { privateStorageConfigured: true, externalImageSearchEnabled: true } } })
    const body = route.request().postData() ? route.request().postDataJSON() : {}
    calls.push({ path, body })
    let data: unknown = {},
      code = 0,
      message = ''
    if (path === '/api/user/get/login') data = { id: uid, userName: '团队成员', userRole: 1 }
    else if (path === '/api/space/get/vo') data = space
    else if (path === '/api/spaceUser/my/list/page') data = { records: [members[1]], total: '1' }
    else if (path === '/api/space/add') data = sid
    else if (path === '/api/spaceUser/list') data = members
    else if (path === '/api/space/list/page') data = { records: [space], total: '1' }
    else if (path === '/api/spaceUser/add') {
      if (failAdd) {
        code = 40000
        message = '用户不存在'
        data = null
      } else {
        members.push({
          id: '9007199254741005',
          userId: String(body.userId),
          userName: '新增成员',
          spaceId: sid,
          spaceRole: String(body.spaceRole),
          owner: false,
          space,
        })
        data = '9007199254741005'
      }
    } else if (path === '/api/spaceUser/edit') {
      members = members.map((m) =>
        m.id === body.id ? { ...m, spaceRole: String(body.spaceRole) } : m,
      )
      data = true
    } else if (path === '/api/spaceUser/delete') {
      members = members.filter((m) => m.id !== body.id)
      data = true
    } else if (path === '/api/picture/tag_category') data = { categoryList: [], tagList: [] }
    else if (path === '/api/picture/list/page/vo')
      data = {
        records: [
          { id: pid, spaceId: sid, userId: '2', name: '团队图', url: '/test.svg', uploadStatus: 1 },
        ],
        total: '1',
      }
    else if (path === '/api/picture/get/vo')
      data = {
        id: pid,
        spaceId: sid,
        userId: '2',
        name: '团队图',
        url: '/test.svg',
        uploadStatus: 1,
      }
    await route.fulfill({ json: { code, data, message } })
  })
  await page.route('**/test.svg', (r) =>
    r.fulfill({
      contentType: 'image/svg+xml',
      body: '<svg xmlns="http://www.w3.org/2000/svg" width="120" height="80"><rect width="120" height="80" fill="teal"/></svg>',
    }),
  )
  return calls
}

for (const role of ['viewer', 'editor', 'admin'])
  test(`${role} sees only permitted team controls even when platform admin`, async ({ page }) => {
    await mockTeam(page, role)
    await page.goto(`/space/${sid}`)
    await expect(page.getByText('团队图', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '上传到此空间' })).toHaveCount(
      role === 'viewer' ? 0 : 1,
    )
    await page.getByRole('button', { name: '图片操作 团队图' }).click()
    await expect(page.getByRole('menuitem', { name: /编辑 \/ 替换/ })).toHaveCount(
      role === 'viewer' ? 0 : 1,
    )
    await page.getByRole('heading', { name: '设计团队' }).click()
    await expect(page.getByRole('button', { name: '成员管理', exact: true })).toHaveCount(
      role === 'admin' ? 1 : 0,
    )
    await expect(page.getByRole('button', { name: '图库分析', exact: true })).toHaveCount(
      role === 'admin' ? 1 : 0,
    )
    await expect(page.getByRole('button', { name: '重命名空间' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '删除空空间' })).toHaveCount(0)
  })

test('viewer cannot enter upload or member management by typing URLs', async ({ page }) => {
  const calls = await mockTeam(page, 'viewer')
  await page.goto(`/picture/add?spaceId=${sid}`)
  await expect(page.getByText('没有此空间的上传或编辑权限')).toBeVisible()
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  await page.goto(`/space/${sid}/members`)
  await expect(page.getByText('没有团队成员管理权限')).toBeVisible()
  expect(calls.some((c) => c.path === '/api/spaceUser/list')).toBe(false)
  await page.goto(`/picture/${pid}`)
  await expect(page.getByText('团队图', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /编\s*辑$/ })).toHaveCount(0)
})

test('team list creation fixes type=1 and redirects to membership page', async ({ page }) => {
  const calls = await mockTeam(page)
  await page.goto('/space/teams')
  await expect(page.getByText('设计团队', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '创建团队空间', exact: true }).click()
  await page.getByPlaceholder('例如：产品素材团队').fill('新团队')
  await page.locator('.ant-modal .ant-btn-primary').click()
  await expect(page).toHaveURL(`/space/${sid}/members`)
  expect(calls.find((c) => c.path === '/api/space/add')?.body).toEqual({
    spaceName: '新团队',
    spaceType: 1,
    spaceLevel: 0,
  })
})

test('member add/remove retains Long IDs and never offers creator removal; mobile usable', async ({
  page,
}, info) => {
  const calls = await mockTeam(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`/space/${sid}/members`)
  await expect(page.getByText('创建者', { exact: true }).first()).toBeVisible()
  const owner = page
    .locator('.member-list .ant-card')
    .filter({ has: page.getByText('创建者', { exact: true }) })
  await expect(owner.getByRole('button', { name: '移除成员' })).toHaveCount(0)
  await page.getByPlaceholder('粘贴完整用户 ID').fill('9007199254741007')
  await page.getByRole('button', { name: '添加成员' }).click()
  await expect(page.getByText('新增成员', { exact: true })).toBeVisible()
  expect(calls.find((c) => c.path === '/api/spaceUser/add')?.body).toEqual({
    spaceId: sid,
    userId: '9007199254741007',
    spaceRole: 'viewer',
  })
  await page
    .locator('.member-list .ant-card')
    .filter({ hasText: '新增成员' })
    .getByRole('button', { name: '移除成员' })
    .click()
  await page.locator('.ant-modal-confirm .ant-btn-primary').click()
  await expect(page.getByText('新增成员', { exact: true })).toHaveCount(0)
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(calls.find((c) => c.path === '/api/spaceUser/delete')?.body).toEqual({
    id: '9007199254741005',
    spaceId: sid,
  })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('team-members-mobile.png'), fullPage: true })
})

test('failed add does not invent membership or erase typed ID', async ({ page }) => {
  await mockTeam(page, 'admin', true)
  await page.goto(`/space/${sid}/members`)
  await page.getByPlaceholder('粘贴完整用户 ID').fill('9007199254741007')
  await page.getByRole('button', { name: '添加成员' }).click()
  await expect(page.getByText('用户不存在', { exact: true })).toBeVisible()
  await expect(page.getByPlaceholder('粘贴完整用户 ID')).toHaveValue('9007199254741007')
  await expect(page.locator('.member-list .ant-card')).toHaveCount(2)
})

test('role edit sends membership ID and scope only after confirmation', async ({ page }) => {
  const calls = await mockTeam(page)
  await page.goto(`/space/${sid}/members`)
  const member = page.locator('.member-list .ant-card').filter({ hasText: '当前成员' })
  await member.locator('.ant-select').click()
  await page.locator('.ant-select-dropdown').getByTitle('编辑者', { exact: true }).click()
  expect(calls.some((c) => c.path === '/api/spaceUser/edit')).toBe(false)
  await page.locator('.ant-modal-confirm .ant-btn-primary').click()
  await expect
    .poll(() => calls.find((c) => c.path === '/api/spaceUser/edit')?.body)
    .toEqual({ id: '202', spaceId: sid, spaceRole: 'editor' })
  await expect(member.locator('.ant-tag').first()).toHaveText('编辑者')
})

test('admin space list filters by type without opening protected picture APIs', async ({
  page,
}) => {
  const calls = await mockTeam(page)
  await page.goto('/admin/spaceManage')
  await expect(page.getByText('设计团队', { exact: true })).toBeVisible()
  await page.locator('.manage-page .ant-select').first().click()
  await page.locator('.ant-select-dropdown').getByTitle('团队空间', { exact: true }).click()
  await page.getByRole('button', { name: /^查\s*询$/ }).click()
  await expect
    .poll(() => calls.filter((c) => c.path === '/api/space/list/page').at(-1)?.body.spaceType)
    .toBe(1)
  expect(calls.some((c) => c.path.startsWith('/api/picture/'))).toBe(false)
})
