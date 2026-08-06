import { expect, test } from '@playwright/test'

// The production overlay serves the app read-only under a path prefix, so both
// specs run against whichever stack SBSA_E2E_URL points at.
const readOnly = process.env.SBSA_E2E_READ_ONLY === 'true'

test('opens the preloaded self-analysis repository', async ({ page }) => {
  await page.goto('./')

  await expect(page.getByRole('heading', { name: 'Spring Boot Static Analysis', exact: true })).toBeVisible()
  await expect(page.getByText('Backend API')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Spring Boot Static Analysis · self-analysis' }))
    .toBeVisible({ timeout: 30_000 })
  await expect(page.getByRole('button', { name: 'Browse endpoints' }))
    .toBeVisible({ timeout: 45_000 })
})

test('hides mutating controls when the deployment is read-only', async ({ page }) => {
  test.skip(!readOnly, 'Only meaningful against a read-only deployment')

  await page.goto('./')
  await expect(page.getByRole('button', { name: 'Browse endpoints' }))
    .toBeVisible({ timeout: 45_000 })

  await expect(page.getByRole('button', { name: 'Add repository' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Remove' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /Index repository|Rescan repository/ }))
    .toHaveCount(0)
  await expect(page.getByText('This is a public read-only deployment.')).toBeVisible()
})
