import { expect, test, type Page } from '@playwright/test'
import { coverSvg } from '../dev/design-preview.js'

test.use({
  video: { mode: 'on', size: { width: 1280, height: 800 } },
  viewport: { width: 1280, height: 800 },
})

const runtimeErrors = new WeakMap<Page, string[]>()
test.beforeEach(({ page }) => {
  const errors: string[] = []
  runtimeErrors.set(page, errors)
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text())
  })
})
test.afterEach(({ page }) => {
  expect(runtimeErrors.get(page)).toEqual([])
})

type MotionSample = {
  view: string
  classes: string
  inert: boolean
  hidden: string | null
  heldHeight: number
  opacity: number
}
type MotionWindow = Window & { motionSamples?: MotionSample[]; motionObserver?: MutationObserver }
const pictures = Array.from({ length: 13 }, (_, index) => ({
  id: String(900100 + index),
  name: `山野之间 ${index}`,
  url: `/motion-art/${index}.svg`,
  thumbnailUrl: `/motion-art/${index}.svg`,
  uploadStatus: index === 1 ? 0 : 1,
  reviewStatus: 1,
  userId: '1',
  category: '自然',
}))
async function setup(page: Page) {
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.route('**/motion-art/*.svg', (route) =>
    route.fulfill({ contentType: 'image/svg+xml', body: coverSvg(0) }),
  )
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const body = route.request().postDataJSON() || {}
    let data: unknown = {}
    if (path === '/api/user/get/login') data = { id: '1', userName: '过渡预览', userRole: 0 }
    else if (path === '/api/picture/tag_category') data = { categoryList: ['自然'], tagList: [] }
    else if (path === '/api/picture/my/list/page/vo' || path === '/api/picture/list/page/vo') {
      const filtered = pictures.filter(
        (picture) => body.uploadStatus === undefined || picture.uploadStatus === body.uploadStatus,
      )
      data = {
        records: filtered
          .slice((body.current - 1) * body.pageSize, body.current * body.pageSize)
          .map((picture) =>
            body.spaceId ? { ...picture, spaceId: String(body.spaceId) } : picture,
          ),
        total: filtered.length,
      }
    } else if (path === '/api/spaceUser/my/list/page') data = { records: [], total: 0 }
    else if (path === '/api/space/my/list/page/vo')
      data = { records: [{ id: '900201', spaceType: 0, userId: '1' }], total: 1 }
    else if (path === '/api/space/get/vo')
      data = {
        id: '900201',
        spaceName: '我的灵感库',
        spaceType: 0,
        userId: '1',
        maxSize: 100000,
        totalSize: 1000,
        totalCount: 13,
        maxCount: 100,
        permissionList: ['picture:view'],
      }
    await route.fulfill({ json: { code: 0, data } })
  })
}

async function observeMotion(page: Page) {
  await page.evaluate(() => {
    const target = window as MotionWindow
    const frames: MotionSample[] = []
    target.motionSamples = frames
    const stage = document.querySelector<HTMLElement>('.route-stage')!
    target.motionObserver = new MutationObserver(() => {
      const view = stage.querySelector<HTMLElement>('.route-page')
      if (!view) return
      frames.push({
        view: view.dataset.view || '',
        classes: view.className,
        inert: view.hasAttribute('inert'),
        hidden: view.getAttribute('aria-hidden'),
        heldHeight: Number.parseFloat(stage.style.minHeight) || 0,
        opacity: Number(getComputedStyle(view).opacity),
      })
    })
    target.motionObserver.observe(stage, { attributes: true, childList: true, subtree: true })
  })
}
async function samples(page: Page) {
  return page.evaluate(() => (window as MotionWindow).motionSamples || [])
}
async function setReducedMotion(page: Page, preference: 'system' | 'browser') {
  if (preference === 'system') await page.emulateMedia({ reducedMotion: 'reduce' })
  else await page.addInitScript(() => localStorage.setItem('tuku:reduce-motion', 'on'))
}
async function settled(page: Page, name: string) {
  const view = page.locator(`.route-page[data-view="${name}"]`)
  await expect(view).toBeVisible()
  await expect(view).not.toHaveClass(/workspace-page-/)
  await expect(view).toHaveCSS('opacity', '1')
  await expect(view).toHaveCSS('transform', 'none')
  await expect(page.locator('.route-stage')).not.toHaveAttribute('style', /min-height/)
  await expect(page.locator('.route-page')).toHaveCount(1)
}

test('content fades in sequence, departing content is inert, shell stays mounted', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/')
  await settled(page, 'home')
  const shell = await page.locator('.topbar').elementHandle()
  const canvas = await page.locator('.particle-background').elementHandle()
  const shellBounds = await page.locator('.topbar').boundingBox()
  await observeMotion(page)
  await page
    .getByRole('navigation', { name: '主导航' })
    .getByRole('link', { name: '我的投稿' })
    .click()
  await settled(page, 'my-pictures')
  const frames = await samples(page)
  expect(
    frames.some(
      (frame) =>
        frame.view === 'home' &&
        frame.classes.includes('leave-active') &&
        frame.inert &&
        frame.hidden === 'true' &&
        frame.heldHeight > 0,
    ),
  ).toBe(true)
  expect(
    frames.some((frame) => frame.view === 'my-pictures' && frame.classes.includes('enter-active')),
  ).toBe(true)
  expect(await shell?.evaluate((element) => element.isConnected)).toBe(true)
  expect(await canvas?.evaluate((element) => element.isConnected)).toBe(true)
  expect(await page.locator('.topbar').boundingBox()).toEqual(shellBounds)
  await page.goBack()
  await settled(page, 'home')
  await expect(page.locator('#homePage')).toBeVisible()
})

test('rapid navigation and automatic space redirect finish on the latest view', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/')
  const nav = page.getByRole('navigation', { name: '主导航' })
  await nav.getByRole('link', { name: '我的投稿' }).click()
  await nav.getByRole('link', { name: '团队空间' }).click()
  await nav.getByRole('link', { name: '探索图库' }).click()
  await settled(page, 'home')
  await expect(page.locator('[inert]')).toHaveCount(0)
  await nav.getByRole('link', { name: '我的空间' }).click()
  await expect(page).toHaveURL('/space/900201')
  await settled(page, 'space-detail')
  await expect(page.locator('.space-picture-card')).toHaveCount(12)
})

test('query-only filters and pagination do not animate or remount the page', async ({ page }) => {
  await setup(page)
  await page.goto('/picture/mine')
  await settled(page, 'my-pictures')
  await expect(page.locator('.submission-card')).toHaveCount(12)
  const view = await page.locator('.route-page').elementHandle()
  await observeMotion(page)
  await page.getByTitle('下一页').click()
  await expect(page.locator('.submission-card')).toHaveCount(1)
  await page.getByRole('button', { name: '草稿', exact: true }).click()
  await expect(page.locator('.submission-status')).toHaveText('草稿')
  expect(await view?.evaluate((element) => element.isConnected)).toBe(true)
  expect((await samples(page)).some((frame) => frame.classes.includes('workspace-page-'))).toBe(
    false,
  )
})

for (const preference of ['system', 'browser'] as const) {
  test(`${preference} reduced-motion preference suppresses route animation`, async ({ page }) => {
    await setup(page)
    await setReducedMotion(page, preference)
    await page.goto('/')
    await settled(page, 'home')
    await observeMotion(page)
    await page
      .getByRole('navigation', { name: '主导航' })
      .getByRole('link', { name: '我的投稿' })
      .click()
    await settled(page, 'my-pictures')
    expect((await samples(page)).some((frame) => frame.classes.includes('workspace-page-'))).toBe(
      false,
    )
  })
}

test('mobile navigation remains usable after transition and late data is shown normally', async ({
  page,
}, info) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByRole('button', { name: '打开导航' }).click()
  await page
    .getByRole('navigation', { name: '移动导航' })
    .getByRole('link', { name: '我的投稿' })
    .click()
  await settled(page, 'my-pictures')
  await expect(page.getByRole('navigation', { name: '移动导航' })).toBeHidden()
  await expect(page.locator('.submission-card')).toHaveCount(12)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: info.outputPath('transition-mobile-settled.png') })
})

test.describe('motion preview', () => {
  test('record a short tour of the new transitions', async ({ page }, info) => {
    await setup(page)
    await page.goto('/')
    await settled(page, 'home')
    const nav = page.getByRole('navigation', { name: '主导航' })
    await nav.getByRole('link', { name: '我的投稿' }).click()
    await settled(page, 'my-pictures')
    await page.getByRole('button', { name: '用户菜单', exact: true }).click()
    await page.getByRole('menuitem', { name: '个人资料' }).click()
    await settled(page, 'user-profile')
    await page
      .getByRole('navigation', { name: '账户导航' })
      .getByRole('link', { name: '偏好与设置' })
      .click()
    await settled(page, 'user-settings')
    await nav.getByRole('link', { name: '探索图库' }).click()
    await settled(page, 'home')
    await expect(page.locator('#homePage')).toBeVisible()
    await page.screenshot({ path: info.outputPath('transition-desktop-settled.png') })
  })
})
