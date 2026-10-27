import { defineConfig, devices } from '@playwright/test'

// Built app on a separate port: legacy API mocks must not intercept Vite source module paths.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 5000 },
  retries: 0,
  workers: 2,
  reporter: 'list',
  outputDir: 'test-results/ui-redesign',
  use: { baseURL: 'http://localhost:5174', headless: true, trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run preview -- --host localhost --port 5174 --strictPort --outDir .ui-dist',
    url: 'http://localhost:5174',
    reuseExistingServer: false,
  },
})
