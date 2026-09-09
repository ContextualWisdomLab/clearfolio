const { chromium } = require('playwright');
const assert = require('assert');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  await page.goto('http://localhost:3000/assets/viewer/demo.html');
  await page.waitForLoadState('networkidle');

  // Click load demo data to populate the history table
  await page.click('#load-demo-data-btn');
  await page.waitForTimeout(2000); // Give time for the data to fetch and render

  // Count the rows in the history table
  const rows = await page.$$('#history-body tr');
  console.log(`Rendered ${rows.length} rows.`);

  // Create a screenshot
  await page.screenshot({ path: '/tmp/history-screenshot.png', fullPage: true });

  await browser.close();

  if (rows.length !== 12) {
    console.error("Failed to render 12 seeded demo items.");
    process.exit(1);
  }
})();
