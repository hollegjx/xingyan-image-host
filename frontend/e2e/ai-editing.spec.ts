import { expect, test, type Page } from '@playwright/test'

const picture = { id: '9007199254741001', userId: '1', name: '测试图片', url: '/fixture-edit.png', picWidth: 600, picHeight: 600, picFormat: 'png', uploadStatus: 1, reviewStatus: 1 }
const taskId = '12345678-1234-1234-1234-123456789abc'
async function setup(page: Page, options: { disabled?: boolean; failed?: boolean; latest?: string; sourceDenied?: boolean } = {}) {
  const png = await page.evaluate(() => { const c = document.createElement('canvas'); c.width = 600; c.height = 600; const x = c.getContext('2d')!; x.fillStyle = '#547bae'; x.fillRect(0, 0, 600, 600); x.fillStyle = 'white'; x.fillRect(60, 60, 120, 120); return c.toDataURL('image/png').split(',')[1]! })
  const calls: { path: string; body: unknown }[] = []
  let current = options.latest || '', queries = 0
  await page.route('**/fixture-edit.png', (r) => r.fulfill({ body: Buffer.from(png, 'base64'), contentType: 'image/png' }))
  await page.route('**/api/**', async (r) => {
    const u = new URL(r.request().url()), path = u.pathname
    const body = r.request().headers()['content-type']?.includes('application/json') && r.request().postData() ? r.request().postDataJSON() : r.request().postData()
    calls.push({ path, body })
    let data: unknown = {}, code = 0, message = ''
    if (path === '/api/user/get/login') data = { id: '1', userName: '测试用户', userRole: 0 }
    else if (path === '/api/picture/get/vo') data = picture
    else if (path === '/api/picture/tag_category') data = { tagList: [], categoryList: [] }
    else if (path === '/api/picture/editing/source') {
      if (!options.sourceDenied) return r.fulfill({ body: Buffer.from(png, 'base64'), contentType: 'application/octet-stream' })
      code = 40101; message = '无权读取'
    } else if (path === '/api/picture/upload') data = { ...picture, url: '/fixture-edit.png?edited=1' }
    else if (path === '/api/picture/capabilities') data = { privateStorageConfigured: true, externalImageSearchEnabled: true }
    else if (path.endsWith('/capabilities')) data = { enabled: !options.disabled, privateEnabled: true }
    else if (path.endsWith('/latest')) data = current ? { id: taskId, status: current, pictureId: picture.id } : undefined
    else if (path.endsWith('/create_task')) { current = 'QUEUED'; data = { id: taskId, status: current, pictureId: picture.id } }
    else if (path.endsWith('/get_task')) { queries++; current = options.failed ? 'FAILED' : current === 'APPLIED' || current === 'APPLYING' ? 'APPLIED' : 'SUCCEEDED'; data = { id: taskId, status: current, pictureId: picture.id, resultUrl: current === 'SUCCEEDED' ? `/api/picture/out_painting/result?taskId=${taskId}` : undefined } }
    else if (path.endsWith('/recover')) { current = 'SUCCEEDED'; data = { id: taskId, status: current, pictureId: picture.id, resultUrl: `/api/picture/out_painting/result?taskId=${taskId}` } }
    else if (path.endsWith('/result')) return r.fulfill({ body: Buffer.from(png, 'base64'), contentType: 'application/octet-stream' })
    else if (path.endsWith('/apply')) { current = 'APPLIED'; data = { ...picture, url: '/fixture-edit.png?applied=1' } }
    await r.fulfill({ json: { code, data, message } })
  })
  await page.goto(`/picture/add?id=${picture.id}`)
  await expect(page.getByRole('button', { name: '编辑图片', exact: true })).toBeVisible()
  return { calls, get queries() { return queries } }
}

test('real cropper loads authenticated Blob, rotates and uploads the same Long id', async ({ page }, info) => {
  const state = await setup(page)
  await page.getByRole('button', { name: '编辑图片', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: '向右旋转' })).toBeEnabled()
  await dialog.getByRole('button', { name: '向右旋转' }).click()
  await dialog.getByText('1:1', { exact: true }).click()
  await page.screenshot({ path: info.outputPath('cropper.png') })
  await dialog.getByRole('button', { name: '保存并替换' }).click()
  await expect(dialog).toHaveCount(0)
  const upload = state.calls.find(c => c.path === '/api/picture/upload')
  expect(upload).toBeDefined()
  expect(String(upload?.body)).toContain('edited.png')
  expect(String(upload?.body)).toContain('测试图片')
  // The ID is a query parameter (generated multipart API), not coerced to a JS Number.
})
test('AI requires consent, polls once, previews then applies and does not resubmit', async ({ page }, info) => {
  const state = await setup(page)
  await page.getByRole('button', { name: 'AI 扩图', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: '生成图片' })).toBeDisabled()
  await expect(dialog.getByText('倍数表示希望扩展的画面视野', { exact: false })).toBeVisible()
  await dialog.getByRole('checkbox').check()
  await dialog.getByRole('button', { name: '生成图片' }).click()
  await expect(dialog.getByRole('img', { name: '扩图结果', exact: true })).toBeVisible({ timeout: 10000 })
  await page.screenshot({ path: info.outputPath('ai-preview.png') })
  await dialog.getByRole('button', { name: '应用结果并替换' }).click()
  await expect(dialog.getByText('已应用', { exact: false }).first()).toBeVisible()
  expect(state.calls.filter(c => c.path.endsWith('/create_task'))).toHaveLength(1)
  expect(state.calls.filter(c => c.path.endsWith('/apply'))).toHaveLength(1)
  expect(state.calls.find(c => c.path.endsWith('/create_task'))?.body).toMatchObject({ pictureId: picture.id, xScale: 2, yScale: 2, externalProcessingConsent: true })
})
test('closed pending dialog stops polling and can restore task without recreating it', async ({ page }) => {
  const state = await setup(page, { latest: 'QUEUED' })
  await page.getByRole('button', { name: 'AI 扩图', exact: true }).click()
  await expect(page.getByText(`任务：${taskId}`, { exact: false })).toBeVisible()
  await page.getByRole('button', { name: 'Close', exact: true }).click()
  // This assertion verifies the absence of polling across an entire poll interval.
  // eslint-disable-next-line playwright/no-wait-for-timeout
  await page.waitForTimeout(3500)
  expect(state.queries).toBe(0)
  await page.getByRole('button', { name: 'AI 扩图', exact: true }).click()
  await expect(page.getByRole('img', { name: '扩图结果', exact: true })).toBeVisible({ timeout: 10000 })
  expect(state.calls.filter(c => c.path.endsWith('/create_task'))).toHaveLength(0)
})
test('disabled service is explicit, cannot generate even after consent', async ({ page }) => {
  await setup(page, { disabled: true })
  await page.getByRole('button', { name: 'AI 扩图', exact: true }).click()
  await expect(page.getByText('AI 扩图尚未启用', { exact: false })).toBeVisible()
  await page.getByRole('checkbox').check()
  await expect(page.getByRole('button', { name: '生成图片' })).toBeDisabled()
})
test('failed and unknown tasks do not show a usable result or auto-retry', async ({ page }) => {
  const state = await setup(page, { latest: 'UNKNOWN' })
  await page.getByRole('button', { name: 'AI 扩图', exact: true }).click()
  await expect(page.getByText('结果未确认', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '生成图片' })).toBeDisabled()
  await expect(page.getByRole('button', { name: '应用结果并替换' })).toHaveCount(0)
  expect(state.calls.filter(c => c.path.endsWith('/create_task'))).toHaveLength(0)
})
test('crop input authorization errors are visible and cannot upload', async ({ page }) => {
  const state = await setup(page, { sourceDenied: true })
  await page.getByRole('button', { name: '编辑图片', exact: true }).click()
  await expect(page.getByText('无权读取')).toBeVisible()
  await expect(page.getByRole('button', { name: '保存并替换' })).toHaveCount(0)
  expect(state.calls.filter(c => c.path === '/api/picture/upload')).toHaveLength(0)
})

test('reopened APPLYING task polls to APPLIED without resubmitting', async ({ page }) => {
  const state = await setup(page, { latest: 'APPLYING' })
  await page.getByRole('button', { name: 'AI 扩图', exact: true }).click()
  await expect(page.getByRole('dialog').getByText('已应用', { exact: true })).toBeVisible({ timeout: 10000 })
  expect(state.queries).toBeGreaterThan(0)
  expect(state.calls.filter(c => c.path.endsWith('/apply') || c.path.endsWith('/create_task'))).toHaveLength(0)
})
test('uncertain apply can recover saved result without generating again', async ({ page }, info) => {
  const state = await setup(page, { latest: 'APPLY_UNKNOWN' })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: 'AI 扩图', exact: true }).click()
  await page.getByRole('button', { name: '核对并恢复已有结果' }).click()
  await expect(page.getByRole('img', { name: '扩图结果', exact: true })).toBeVisible()
  await page.screenshot({ path: info.outputPath('ai-recovery-mobile.png') })
  expect(state.calls.filter(c => c.path.endsWith('/recover'))).toHaveLength(1)
  expect(state.calls.filter(c => c.path.endsWith('/apply') || c.path.endsWith('/create_task'))).toHaveLength(0)
})
