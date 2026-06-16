import { test, expect } from '@playwright/test'
import { installOverlay, caption, glideTo, glideClick } from './overlay'

// Full-tour demo: AI import -> compliance check -> rule review. Records to demo-artifacts/*.webm.
// Requires the app running: frontend :5173 + backend :8080 under the `demo` Spring profile.
test('compliance-checker full tour', async ({ page }) => {
  await installOverlay(page)
  await page.goto('/')
  await expect(page.getByRole('button', { name: 'Editor' })).toBeVisible()
  await page.waitForTimeout(800)

  // 1) AI import
  await caption(page, '1 · Upload a floor plan — AI extracts spaces, doors & exits')
  await glideTo(page, page.locator('input[type="file"]'))
  await page.setInputFiles('input[type="file"]', 'e2e/assets/plan.jpg')
  const confirm = page.getByRole('button', { name: 'Confirm & load into editor' })
  await expect(confirm).toBeEnabled({ timeout: 20_000 })   // scaleGuess pre-fills -> enabled
  await page.waitForTimeout(1400)                          // let the reviewer see the extracted plan
  await glideClick(page, confirm)

  // 2) Check compliance
  await caption(page, '2 · One click → located NZBC C/AS2 egress violations')
  const check = page.getByRole('button', { name: 'Check compliance' })
  await glideClick(page, check)
  await expect(page.getByText(/violation\(s\)/)).toBeVisible({ timeout: 25_000 })
  await page.waitForTimeout(1800)                          // hold on the red space + dashed path

  // 3) Rule review
  await caption(page, '3 · Rules are AI-codified from C/AS2 text, then human-approved')
  await glideClick(page, page.getByRole('button', { name: 'Rule review' }))
  await expect(page.getByRole('heading', { name: 'Rule review' })).toBeVisible()
  const approve = page.getByRole('button', { name: 'Approve' }).first()
  await expect(approve).toBeVisible({ timeout: 10_000 })
  await page.waitForTimeout(1200)
  await glideClick(page, approve)
  await page.waitForTimeout(1500)

  await caption(page, '')
})
