import { defineConfig, devices } from '@playwright/test';
 
const isCI = !!process.env.CI;
 
export default defineConfig({
 
    // Location of UI test files
    testDir: './tests',
 
    // Match only Playwright spec files
    testMatch: '**/*.spec.ts',
 
    // Run independent tests in parallel
    fullyParallel: true,
 
    // Prevent accidental test.only in CI
    forbidOnly: isCI,
 
    // Retry failed tests only in CI
    retries: isCI ? 2 : 0,
 
    // Control parallel execution
    workers: isCI ? 2 : undefined,
 
    // Maximum duration for one test
    timeout: 30_000,
 
    // Assertion timeout
    expect: {
        timeout: 5_000,
    },
 
    // Reports
    reporter: [
        ['list'],
        [
            'html',
            {
                outputFolder: 'playwright-report',
                open: 'never',
            },
        ],
    ],
 
    // Test artifacts
    outputDir: 'test-results',
 
    // Common settings for all tests
    use: {
        // Automation Exercise base URL
        baseURL: 'http://localhost:8080',
 
        // Microsoft Edge only
        ...devices['Desktop Edge'],
        channel: 'msedge',
 
        // Browser execution mode
        headless: isCI,
 
        // Timeouts for browser actions
        actionTimeout: 10_000,
        navigationTimeout: 20_000,
 
        // Debugging artifacts
        screenshot: 'only-on-failure',
        video: 'retain-on-failure',
        trace: 'on-first-retry',
 
        // Viewport
        viewport: {
            width: 1440,
            height: 900,
        },
 
        // Fresh context for each test
        ignoreHTTPSErrors: false,
    },
 
    // A single browser project: Edge
    projects: [
        {
            name: 'Microsoft Edge',
            use: {
                ...devices['Desktop Edge'],
                channel: 'msedge',
            },
        },
    ],
});