import { expect, test } from '@playwright/test'

test('opens the preloaded self-analysis repository', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: 'Code Atlas', exact: true })).toBeVisible()
  await expect(page.getByText('Backend API')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Code Atlas · self-analysis' }))
    .toBeVisible({ timeout: 30_000 })
  await expect(page.getByRole('button', { name: 'Browse endpoints' }))
    .toBeVisible({ timeout: 45_000 })
})
