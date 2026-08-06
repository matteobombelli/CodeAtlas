import { expect, test } from '@playwright/test'

test('opens the preloaded self-analysis repository', async ({ page }) => {
  await page.goto('/')

  await expect(
    page.getByRole('heading', { level: 1, name: 'Spring Boot Static Analysis Map' }),
  ).toBeVisible()
  await expect(page.getByRole('combobox', { name: 'Project' })).toHaveValue(
    /.+/,
    { timeout: 30_000 },
  )
  await expect(
    page.getByRole('combobox', { name: 'Project' }).locator('option:checked'),
  ).toHaveText('Spring Boot Static Analysis source')
  await expect(
    page.getByRole('combobox', { name: 'Project' }).locator('option[value="__add-project__"]'),
  ).toHaveText('Add another project…')
  await expect(page.getByRole('searchbox', { name: 'Find code to inspect' }))
    .toBeVisible({ timeout: 45_000 })
})
