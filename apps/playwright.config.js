const { defineConfig } = require("@playwright/test");

module.exports = defineConfig({
  testDir: "./browser-tests",
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: "line",
  use: {
    baseURL: "http://localhost:6660",
    channel: "chrome",
    headless: true,
    trace: "retain-on-failure",
  },
  webServer: [
    {
      command: "npm run dev",
      url: "http://localhost:6660",
      reuseExistingServer: false,
      timeout: 120_000,
    },
    {
      command: "node browser-tests/mock-omni-server.js",
      url: "http://127.0.0.1:6661/health",
      reuseExistingServer: false,
      timeout: 30_000,
    },
  ],
});
