import { test, expect, type Page } from '@playwright/test'

const sid = '9007199254740993'
function snapshot(scope = 'SPACE') {
  return {
    scope,
    spaceId: scope === 'SPACE' ? sid : undefined,
    spaceName: scope === 'SPACE' ? '我的旅行图库' : scope === 'PUBLIC' ? '公共图库' : '全站图库',
    generatedAt: '2026-10-24T15:00:00+08:00',
    usage: {
      usedSize: '52428800',
      usedCount: '32',
      completedCount: '30',
      draftCount: '2',
      ...(scope === 'SPACE'
        ? { maxSize: '104857600', maxCount: '100', sizeUsageRatio: 50, countUsageRatio: 32 }
        : {}),
    },
    categories:
      scope === 'ALL'
        ? []
        : [
            { name: '旅行', count: '18' },
            { name: '日常', count: '12' },
          ],
    tags:
      scope === 'ALL'
        ? []
        : [
            { name: '山川', count: '12' },
            { name: '建筑', count: '6' },
          ],
    sizes: [
      { name: '小于 100 KiB', count: '8' },
      { name: '100～512 KiB', count: '22' },
    ],
    trend: {
      startDate: '2026-09-25',
      endDate: '2026-10-24',
      timezone: 'Asia/Shanghai',
      points: [
        { name: '2026-10-22', count: '5' },
        { name: '2026-10-23', count: '0' },
        { name: '2026-10-24', count: '9' },
      ],
    },
    rankings:
      scope === 'ALL'
        ? [{ spaceId: sid, spaceName: '旅行图库', usedSize: '52428800', usedCount: '32' }]
        : [],
  }
}
async function mock(page: Page, admin = false, fail = false, empty = false) {
  const bodies: Record<string, unknown>[] = []
  await page.route('**/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    let data: unknown = {}
    let code = 0
    if (path === '/api/user/get/login')
      data = { id: '2', userName: '分析测试', userRole: admin ? 1 : 0 }
    if (path === '/api/space/analyze/dashboard') {
      const body = route.request().postDataJSON()
      bodies.push(body)
      data = snapshot(body.scope)
      if (empty)
        data = {
          ...snapshot(body.scope),
          usage: {
            usedSize: '0',
            usedCount: '0',
            completedCount: '0',
            draftCount: '0',
            maxSize: '0',
            maxCount: '0',
          },
          categories: [],
          tags: [],
          sizes: [],
          trend: { points: [] },
        }
      if (fail) {
        code = 40101
        data = null
      }
    }
    await route.fulfill({ json: { code, data, message: code ? '仅空间所有者可查看统计' : '' } })
  })
  return bodies
}

test('private dashboard preserves ID, renders charts and tables, supports mobile', async ({
  page,
}, info) => {
  const bodies = await mock(page)
  await page.goto(`/space/${sid}/analyze`)
  await expect(page.getByText('我的旅行图库', { exact: true })).toBeVisible()
  expect(bodies[0]?.spaceId).toBe(sid)
  await expect(page.locator('.plot svg')).toHaveCount(4)
  await page.getByText('分类分布数据表', { exact: true }).click()
  await expect(page.getByRole('row', { name: '旅行 18' })).toBeVisible()
  await page.getByLabel('趋势维度', { exact: true }).selectOption('week')
  await expect.poll(() => bodies.at(-1)?.timeDimension).toBe('week')
  await expect(page.locator('.ant-spin-spinning')).toHaveCount(0)
  await page.screenshot({
    path: info.outputPath('analysis-desktop.png'),
    fullPage: true,
    animations: 'disabled',
  })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('.plot svg')).toHaveCount(4)
  await expect
    .poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth))
    .toBe(true)
  await page.screenshot({
    path: info.outputPath('analysis-mobile.png'),
    fullPage: true,
    animations: 'disabled',
  })
})

test('admin global aggregate hides private classification and exposes no private gallery link', async ({
  page,
}) => {
  const bodies = await mock(page, true)
  await page.goto('/admin/spaceAnalyze')
  await expect(page.getByText('公共图库', { exact: true }).last()).toBeVisible()
  await page.getByLabel('统计范围', { exact: true }).selectOption('ALL')
  await expect(page.getByText('空间容量排行 Top 10', { exact: true })).toBeVisible()
  await expect(page.getByText('热门标签 Top 30', { exact: true })).toHaveCount(0)
  await expect(page.getByText(`ID ${sid}`, { exact: true })).toBeVisible()
  await expect(page.locator(`a[href*="/space/${sid}"]`)).toHaveCount(0)
  expect(bodies.at(-1)).not.toHaveProperty('spaceId')
  expect(bodies.at(-1)).not.toHaveProperty('userId')
})

test('invalid ID and backend denial never display another scope or stale statistics', async ({
  page,
}) => {
  const bodies = await mock(page, false, true)
  await page.goto('/space/0/analyze')
  await expect(page.getByText('空间 ID 不合法', { exact: true })).toBeVisible()
  expect(bodies).toHaveLength(0)
  await page.goto(`/space/${sid}/analyze`)
  await expect(page.getByText('仅空间所有者可查看统计', { exact: true })).toBeVisible()
  await expect(page.getByTestId('analysis-dashboard')).toHaveCount(0)
})

test('empty data and zero quota do not render misleading charts or percentages', async ({
  page,
}) => {
  await mock(page, false, false, true)
  await page.goto(`/space/${sid}/analyze`)
  await expect(page.getByText('配额为零，使用率不适用')).toBeVisible()
  await expect(page.getByText('暂无统计数据')).toHaveCount(4)
  await expect(page.locator('.plot svg')).toHaveCount(0)
})

test('date filters affect request and validation does not retain previous result', async ({
  page,
}) => {
  const bodies = await mock(page)
  await page.goto(`/space/${sid}/analyze`)
  await expect(page.getByTestId('analysis-dashboard')).toBeVisible()
  await page.getByLabel('开始日期', { exact: true }).fill('2025-01-01')
  await page.getByRole('button', { name: '查询 / 刷新' }).click()
  await expect(page.getByText('请同时填写开始和结束日期')).toBeVisible()
  await expect(page.getByTestId('analysis-dashboard')).toHaveCount(0)
  expect(bodies).toHaveLength(1)
  await page.getByLabel('结束日期', { exact: true }).fill('2025-01-31')
  await page.getByRole('button', { name: '查询 / 刷新' }).click()
  await expect.poll(() => bodies.at(-1)?.endDate).toBe('2025-01-31')
  await expect(page.getByTestId('analysis-dashboard')).toBeVisible()
})

test('an older delayed response cannot overwrite the selected global scope', async ({ page }) => {
  await mock(page, true)
  let release: () => void = () => {}
  const held = new Promise<void>((resolve) => {
    release = resolve
  })
  let oldStarted: () => void = () => {}
  const started = new Promise<void>((resolve) => {
    oldStarted = resolve
  })
  let oldFinished: () => void = () => {}
  const finished = new Promise<void>((resolve) => {
    oldFinished = resolve
  })
  await page.route('**/api/space/analyze/dashboard', async (route) => {
    const body = route.request().postDataJSON()
    if (body.scope === 'PUBLIC') {
      oldStarted()
      await held
    }
    try {
      await route.fulfill({ json: { code: 0, data: snapshot(body.scope) } })
    } finally {
      if (body.scope === 'PUBLIC') oldFinished()
    }
  })
  await page.goto('/admin/spaceAnalyze')
  await started
  await page.getByLabel('统计范围', { exact: true }).selectOption('ALL')
  await expect(page.getByText('全站图库', { exact: true })).toBeVisible()
  release()
  await finished
  await expect(page.getByText('全站图库', { exact: true })).toBeVisible()
  await expect(page.getByText('热门标签 Top 30', { exact: true })).toHaveCount(0)
})

test('a response for a different private space is not rendered', async ({ page }) => {
  await mock(page)
  await page.route('**/api/space/analyze/dashboard', (route) =>
    route.fulfill({
      json: { code: 0, data: { ...snapshot(), spaceId: '999' } },
    }),
  )
  await page.goto(`/space/${sid}/analyze`)
  await expect(page.getByText('统计返回的范围不匹配，请重试')).toBeVisible()
  await expect(page.getByTestId('analysis-dashboard')).toHaveCount(0)
})
