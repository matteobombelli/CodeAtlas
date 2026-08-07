import { expect, test } from '@playwright/test'

const readOnly =
  process.env.SPRING_BOOT_STATIC_ANALYSIS_E2E_READ_ONLY === 'true'

test('opens the preloaded self-analysis repository', async ({ page }) => {
  await page.goto('./')

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
  await expect(page.getByRole('searchbox', { name: 'Find code to inspect' }))
    .toBeVisible({ timeout: 45_000 })
  await expect(
    page.getByRole('complementary', { name: 'Selection details' })
      .getByRole('heading', { name: 'Source' }),
  ).toBeVisible({ timeout: 45_000 })

  const blastRadius = page.getByRole('button', { name: /Blast radius/ })
  await blastRadius.click()
  await expect(blastRadius).toHaveAttribute('aria-pressed', 'true')
})

test('matches mutation controls to the server deployment mode', async ({ page }) => {
  await page.goto('./')
  const addProject = page
    .getByRole('combobox', { name: 'Project' })
    .locator('option[value="__add-project__"]')

  if (readOnly) {
    await expect(addProject).toHaveCount(0)
  } else {
    await expect(addProject).toHaveText('Add another project…')
  }
})
