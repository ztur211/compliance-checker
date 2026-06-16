import type { Page, Locator } from '@playwright/test'

// Injected into the page so the recording shows a moving cursor, click ripples, and step captions —
// raw Playwright video renders none of these and looks broken. The init script tracks the synthetic
// mouse Playwright dispatches; helpers drive smooth motion and set captions.
const INIT = `
(() => {
  const cur = document.createElement('div'); cur.className = 'cursor-demo'; cur.style.left='-50px'; cur.style.top='-50px';
  const cap = document.createElement('div'); cap.className = 'caption-bar';
  const add = () => { document.body.appendChild(cur); document.body.appendChild(cap); };
  if (document.body) add(); else addEventListener('DOMContentLoaded', add);
  addEventListener('mousemove', (e) => { cur.style.left = e.clientX + 'px'; cur.style.top = e.clientY + 'px'; }, true);
  addEventListener('mousedown', (e) => {
    const r = document.createElement('div'); r.className = 'cursor-ripple';
    r.style.left = e.clientX + 'px'; r.style.top = e.clientY + 'px'; document.body.appendChild(r);
    setTimeout(() => r.remove(), 600);
  }, true);
  window.__caption = (t) => { cap.textContent = t; cap.classList.toggle('show', !!t); };
})();
`

export async function installOverlay(page: Page): Promise<void> {
  await page.addInitScript(INIT)
}

export async function caption(page: Page, text: string): Promise<void> {
  await page.evaluate((t) => (window as unknown as { __caption: (s: string) => void }).__caption(t), text)
}

/** Smoothly move the synthetic cursor to the centre of a locator (so the overlay animates), then pause. */
export async function glideTo(page: Page, target: Locator, pauseMs = 350): Promise<void> {
  await target.scrollIntoViewIfNeeded()
  const box = await target.boundingBox()
  if (box) {
    await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 28 })
  }
  await page.waitForTimeout(pauseMs)
}

/** Glide to a control and click it. */
export async function glideClick(page: Page, target: Locator): Promise<void> {
  await glideTo(page, target)
  await target.click()
}
