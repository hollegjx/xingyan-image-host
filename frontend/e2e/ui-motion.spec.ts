import { test, expect, type Page } from '@playwright/test'
import { coverSvg } from '../dev/design-preview.js'

const picture = {
  id: '9007199254741009',
  userId: '1',
  name: '山野之间',
  url: '/motion-fixture.svg',
  uploadStatus: 1,
}
async function setup(page: Page, options: { failure?: boolean; delayed?: boolean } = {}) {
  const uploads: URL[] = []
  let release: () => void = () => {}
  const gate = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/motion-fixture.svg', (route) =>
    route.fulfill({ contentType: 'image/svg+xml', body: coverSvg(0) }),
  )
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url())
    let data: unknown = {},
      code = 0,
      message = ''
    if (url.pathname === '/api/user/get/login')
      data = { id: '1', userName: '设计示例', userRole: 0 }
    if (url.pathname === '/api/picture/tag_category') data = { categoryList: [], tagList: [] }
    if (url.pathname === '/api/picture/list/page/vo') data = { records: [], total: 0 }
    if (url.pathname === '/api/picture/get/vo') data = picture
    if (url.pathname === '/api/picture/upload') {
      uploads.push(url)
      if (options.delayed) await gate
      if (options.failure) {
        code = 50000
        message = '模拟上传失败'
      } else data = picture
    }
    await route.fulfill({ json: { code, data, message } })
  })
  return { uploads, release }
}
const image = {
  name: 'landscape.png',
  mimeType: 'image/png',
  buffer: Buffer.from('mocked image content'),
}

test('primary button lifts, uploader unfolds on hover and drag, and keyboard opens file selection', async ({
  page,
}, info) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await setup(page)
  await page.goto('/')
  const create = page.getByRole('link', { name: '创建图片' })
  const top = (await create.boundingBox())!.y
  await create.hover()
  await expect.poll(async () => (await create.boundingBox())!.y).toBeLessThan(top - 1)
  await create.click()
  await expect(page.getByRole('heading', { name: '创建图片', exact: true })).toBeVisible()
  const zone = page.locator('.ant-upload-drag')
  const paper = page.locator('.paper-front')
  const folded = await paper.evaluate((el) => getComputedStyle(el).transform)
  await zone.hover()
  await expect.poll(() => paper.evaluate((el) => getComputedStyle(el).transform)).not.toBe(folded)
  await expect(zone).not.toHaveCSS('transform', 'none')
  await page.screenshot({ path: info.outputPath('upload-hover-desktop.png') })
  await page.mouse.move(240, 90)
  await zone.dispatchEvent('dragover')
  await expect(zone).toHaveClass(/ant-upload-drag-hover/)
  await zone.dispatchEvent('dragleave')
  await expect(zone).not.toHaveClass(/ant-upload-drag-hover/)
  const chooser = page.waitForEvent('filechooser')
  await page.locator('.ant-upload-btn').press('Enter')
  await (await chooser).setFiles(image)
  await expect(page.locator('.file-preview')).toHaveAttribute('src', picture.url)
  await expect(page.getByRole('button', { name: '保存图片信息' })).toBeVisible()
})

test('dropped replacement preserves ID; removing preview is local and invalid files are rejected', async ({
  page,
}, info) => {
  const state = await setup(page)
  await page.goto(`/picture/add?id=${picture.id}`)
  await expect(page.locator('.file-preview')).toBeVisible()
  const preview = await page.locator('.file-preview-card').boundingBox()
  const metadata = await page.locator('.picture-metadata').boundingBox()
  expect(preview!.y + preview!.height).toBeLessThan(metadata!.y)
  const edit = await page.getByRole('button', { name: '编辑图片', exact: true }).boundingBox()
  expect(edit!.y + edit!.height).toBeLessThan(metadata!.y)
  await page.screenshot({ path: info.outputPath('upload-edit-desktop.png'), fullPage: true })
  await expect(page.locator('input[type=file]')).toHaveCount(0)
  await page.getByRole('button', { name: '替换图片' }).click()
  await page.getByRole('button', { name: '移除预览' }).click()
  await expect(page.locator('.file-preview')).toHaveCount(0)
  expect(state.uploads).toHaveLength(0)
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'bad.txt', mimeType: 'text/plain', buffer: Buffer.from('no') })
  await expect(page.getByText('仅支持 JPG、PNG、GIF、WEBP 图片')).toBeVisible()
  expect(state.uploads).toHaveLength(0)
  const transfer = await page.evaluateHandle(() => {
    const data = new DataTransfer()
    data.items.add(new File(['mock image'], 'replacement.png', { type: 'image/png' }))
    return data
  })
  await page.locator('.ant-upload-btn').dispatchEvent('drop', { dataTransfer: transfer })
  await transfer.dispose()
  await expect(page.locator('.file-preview')).toBeVisible()
  expect(state.uploads).toHaveLength(1)
  expect(state.uploads[0]!.searchParams.get('id')).toBe(picture.id)
})

test('busy upload cannot animate or resubmit and failure restores the original preview', async ({
  page,
}) => {
  const state = await setup(page, { delayed: true, failure: true })
  await page.goto(`/picture/add?id=${picture.id}`)
  await page.getByRole('button', { name: '替换图片' }).click()
  await page.locator('input[type=file]').setInputFiles(image)
  await expect(page.getByRole('status').filter({ hasText: /正在上传|文件已送达/ })).toBeVisible()
  await expect(page.locator('input[type=file]')).toBeDisabled()
  const zone = page.locator('.ant-upload-drag')
  await zone.hover()
  await expect(zone).toHaveCSS('transform', 'none')
  expect(state.uploads).toHaveLength(1)
  state.release()
  await expect(page.getByText('图片上传失败，模拟上传失败')).toBeVisible()
  await expect(page.locator('.file-preview')).toHaveAttribute('src', picture.url)
  await expect(page.locator('input[type=file]')).toBeEnabled()
})

test('reduced-motion disables hover movement; touch layout stays within viewport', async ({
  page,
  browser,
}, info) => {
  await page.emulateMedia({ reducedMotion: 'reduce' })
  await setup(page)
  await page.goto('/picture/add')
  const paper = page.locator('.paper-front')
  const initial = await paper.evaluate((el) => getComputedStyle(el).transform)
  await page.locator('.ant-upload-drag').hover()
  await expect(page.locator('.ant-upload-drag')).toHaveCSS('transform', 'none')
  await expect(paper).toHaveCSS('transform', initial)
  const create = page.getByRole('link', { name: '创建图片' })
  await create.hover()
  await expect(create).toHaveCSS('transform', 'none')
  const context = await browser.newContext({
    viewport: { width: 390, height: 844 },
    isMobile: true,
    hasTouch: true,
  })
  const mobile = await context.newPage()
  await setup(mobile)
  await mobile.goto('/picture/add')
  await expect(mobile.getByRole('heading', { name: '把这一帧，收进檐下' })).toBeVisible()
  expect(await mobile.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await mobile.screenshot({ path: info.outputPath('upload-mobile.png'), fullPage: true })
  await context.close()
})
