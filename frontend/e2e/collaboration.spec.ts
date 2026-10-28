import { test, expect, type BrowserContext, type WebSocketRoute } from '@playwright/test'

const id = '9007199254741003'
async function mock(context: BrowserContext) {
  const sockets = new Map<WebSocketRoute, number>()
  let owner: WebSocketRoute | undefined,
    revision = 0,
    rotation = 0,
    zoom = 0,
    next = 0
  let upload = ''
  const broadcast = () => {
    for (const [ws] of sockets)
      ws.send(
        JSON.stringify({
          type: 'STATE',
          state: {
            roomId: 'room',
            sourceVersion: 'source',
            revision: String(revision),
            rotation,
            zoom,
            editorId: owner ? '42' : null,
            editorName: '测试成员',
            editing: ws === owner,
            leaseToken: ws === owner ? 'private-owner-token' : null,
            saving: false,
          },
        }),
      )
  }
  await context.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path === '/api/picture/capabilities') return route.fulfill({ json: { code: 0, data: { privateStorageConfigured: true, externalImageSearchEnabled: true } } })
    if (path.endsWith('/editing/source'))
      return route.fulfill({
        contentType: 'image/svg+xml',
        body: '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="80"><rect width="160" height="80" fill="teal"/></svg>',
      })
    if (path.endsWith('/picture/upload')) {
      upload = route.request().postData() || ''
      return route.fulfill({ json: { code: 50001, message: '模拟事务保存失败' } })
    }
    let data: unknown = {}
    if (path.endsWith('/user/get/login')) data = { id: '42', userName: '测试成员', userRole: 0 }
    if (path.endsWith('/picture/get/vo'))
      data = { id, spaceId: '99', name: '协同测试', category: '测试', tags: ['保留标签'] }
    await route.fulfill({ json: { code: 0, data } })
  })
  await context.routeWebSocket('**/api/ws/picture/edit?*', (ws) => {
    sockets.set(ws, ++next)
    broadcast()
    ws.onMessage((raw) => {
      const m = JSON.parse(raw as string)
      if (m.type === 'ENTER_EDIT' && !owner) {
        owner = ws
        revision++
      }
      if (m.type === 'EXIT_EDIT' && owner === ws) {
        owner = undefined
        revision++
      }
      if (m.type === 'EDIT_ACTION' && owner === ws && m.revision === String(revision)) {
        if (m.action === 'ROTATE_RIGHT') rotation = (rotation + 1) % 4
        if (m.action === 'ROTATE_LEFT') rotation = (rotation + 3) % 4
        if (m.action === 'ZOOM_IN') zoom++
        if (m.action === 'ZOOM_OUT') zoom--
        revision++
      }
      broadcast()
    })
    ws.onClose(() => {
      sockets.delete(ws)
      if (owner === ws) {
        owner = undefined
        revision++
      }
      broadcast()
    })
  })
  return {
    upload: () => upload,
    revoke: () => {
      for (const ws of sockets.keys()) ws.close({ code: 1008, reason: 'revoked' })
      sockets.clear()
    },
  }
}

test('two tabs show one authoritative edit and release the lease after disconnect', async ({
  context,
}) => {
  await mock(context)
  const first = await context.newPage(),
    second = await context.newPage()
  await first.goto(`/picture/${id}/collaborate`)
  await second.goto(`/picture/${id}/collaborate`)
  await first.getByRole('button', { name: '取得编辑权' }).click()
  await expect(second.getByRole('button', { name: '取得编辑权' })).toBeDisabled()
  await first.getByRole('button', { name: '向右旋转' }).click()
  await expect(first.locator('.version')).toContainText('旋转 90°')
  await expect(second.locator('.version')).toContainText('旋转 90°')
  await expect(second.getByLabel('协同编辑预览')).toHaveAttribute('width', '80')
  await expect(second.getByLabel('协同编辑预览')).toHaveAttribute('height', '160')
  await first.close()
  await second.getByRole('button', { name: '取得编辑权' }).click()
  await expect(second.getByRole('button', { name: '向左旋转' })).toBeEnabled()
})

test('save sends token and revision in multipart body and keeps state on failure', async ({
  context,
  page,
}) => {
  const fixture = await mock(context)
  await page.goto(`/picture/${id}/collaborate`)
  await page.getByRole('button', { name: '取得编辑权' }).click()
  await page.getByRole('button', { name: '放大', exact: true }).click()
  await page.getByRole('button', { name: '保存图片' }).click()
  await expect(page.getByText(/模拟事务保存失败/)).toBeVisible()
  expect(fixture.upload()).toContain('collaborationToken')
  expect(fixture.upload()).toContain('private-owner-token')
  expect(fixture.upload()).toContain('collaborationRevision')
  expect(fixture.upload()).toContain('保留标签')
  await expect(page.locator('.version')).toContainText('缩放 110%')
  await expect(page.getByRole('button', { name: '保存图片' })).toBeEnabled()
})

test('revocation clears private preview and mobile toolbar stays within viewport', async ({
  context,
  page,
}, testInfo) => {
  const fixture = await mock(context)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto(`/picture/${id}/collaborate`)
  await expect(page.getByLabel('协同编辑预览')).toBeVisible()
  await expect(page.getByRole('button', { name: '取得编辑权' })).toBeEnabled()
  const heading = await page.locator('.heading').boundingBox()
  const info = await page.getByRole('alert').first().boundingBox()
  expect(info!.y).toBeGreaterThanOrEqual(heading!.y + heading!.height + 12)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  )
  await page.screenshot({ path: testInfo.outputPath('collaboration-mobile-mocked.png'), fullPage: true })
  fixture.revoke()
  await expect(page.getByLabel('协同编辑预览')).toBeHidden()
  await expect(page.getByRole('button', { name: '保存图片' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '重新连接' })).toBeVisible()
})
