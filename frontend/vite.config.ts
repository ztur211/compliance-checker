import { defineConfig, type UserConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The `test` field is read by Vitest at runtime. Vite's own UserConfig type does
// not declare it, and Vite 8 / Vitest 3 ship mismatched internal Vite type copies,
// so we attach it via a typed variable (excess-property checks don't apply to
// variables) rather than importing `defineConfig` from `vitest/config`.
const config: UserConfig & { test: unknown } = {
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/setupTests.ts',
  },
}

export default defineConfig(config)
