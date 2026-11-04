import { expect, test, type Browser, type Page } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');
// Without a GPU, Chromium draws WebGL in software, which is slow.
test.setTimeout(300_000);
const slow = expect.configure({ timeout: 45_000 });

async function onEngineering(browser: Browser, email: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/login');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('button', { name: 'Enter Corridor HQ' }).click();
  await page
    .getByRole('navigation', { name: 'Floors' })
    .getByRole('link', { name: /Engineering/ })
    .click();
  await slow(page).toHaveURL(/\/office\/floors\//);
  return page;
}

test('two people on a floor see each other and walk around', async ({ browser }) => {
  const nour = await onEngineering(browser, 'nour@corridor.local');
  await slow(nour.getByText('You', { exact: true })).toBeVisible();

  const omar = await onEngineering(browser, 'omar@corridor.local');
  await slow(omar.getByText('Nour', { exact: true }).first()).toBeVisible();
  await slow(nour.getByText('Omar', { exact: true }).first()).toBeVisible();
  await slow(nour.getByText("You're the first one on this floor.")).toBeHidden();

  // Walking with the keyboard keeps both avatars on the floor; leaving takes Omar off Nour's view.
  await nour.keyboard.press('ArrowUp');
  await omar.close();
  await slow(nour.getByText('Omar', { exact: true })).toHaveCount(0);
  await slow(nour.getByText('You', { exact: true })).toBeVisible();
});
