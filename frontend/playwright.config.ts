import { defineConfig } from '@playwright/test'

// Records the full-tour demo to webm. Headed + slowMo for natural pacing; a fixed viewport so the
// frame is stable for export. Assumes the app is already running: frontend :5173 (vite, proxying
// /api -> :8080) and backend :8080 under the `demo` Spring profile.
export default defineConfig({
  testDir: './e2e',
  outputDir: './demo-artifacts',
  timeout: 120_000,
  retries: 0,
  workers: 1,
  use: {
    baseURL: 'http://localhost:5173',
    headless: false,
    viewport: { width: 1280, height: 800 },
    video: { mode: 'on', size: { width: 1280, height: 800 } },
    launchOptions: { slowMo: 120 },
  },
})
