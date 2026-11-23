import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  timeout: 45000,
  use: {
    baseURL: process.env.CAMPUS_FRONTEND_URL || 'http://127.0.0.1:5173',
    launchOptions: { executablePath: process.env.CAMPUS_BROWSER_EXECUTABLE || undefined },
    screenshot: 'only-on-failure'
  }
})
