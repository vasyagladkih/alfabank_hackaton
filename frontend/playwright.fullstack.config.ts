import { defineConfig, devices } from '@playwright/test';

// An already running real Nginx + Java + PostgreSQL stack is required.
// No preview server or API transport fixtures belong to this suite.
export default defineConfig({
  testDir: './fullstack',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-fullstack-report' }]],
  outputDir: 'fullstack-results',
  use: {
    baseURL: process.env.REAL_STACK_BASE_URL ?? 'http://localhost:3000',
    locale: 'ru-RU', timezoneId: 'Europe/Moscow', reducedMotion: 'reduce',
    screenshot: 'only-on-failure', trace: 'retain-on-failure',
  },
  projects: [{ name: 'real-chromium', use: { ...devices['Desktop Chrome'] } }],
});
