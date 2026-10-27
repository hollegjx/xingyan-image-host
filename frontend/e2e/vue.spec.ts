import { test, expect } from '@playwright/test'

test('renders the public picture home page', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: '为每一张图片， 留一处屋檐。' })).toBeVisible()
  await expect(
    page.getByRole('searchbox').or(page.getByRole('textbox', { name: '搜索图片' })),
  ).toBeVisible()
})
