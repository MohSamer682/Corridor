import { expect, test, type Browser, type Page } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');
// Without a GPU, Chromium draws WebGL in software, which is slow.
test.setTimeout(480_000);
const slow = expect.configure({ timeout: 120_000 });

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

test('two people who arrive together talk and chat', async ({ browser }) => {
  // Both arrive next to the lift, well inside hearing range.
  const nour = await onEngineering(browser, 'nour@corridor.local');
  const omar = await onEngineering(browser, 'omar@corridor.local');

  await slow(nour.getByRole('group', { name: 'Omar, connected' })).toBeVisible();
  await slow(omar.getByRole('group', { name: 'Nour, connected' })).toBeVisible();

  await nour
    .getByRole('textbox', { name: 'Message' })
    .fill('Got a minute for the release checklist?');
  await nour.getByRole('button', { name: 'Send' }).click();
  await slow(omar.getByText('Got a minute for the release checklist?')).toBeVisible();

  await omar.getByRole('button', { name: /^Focus mode/ }).click();
  await slow(omar.getByText('Focus mode on. Nobody can call or message you.')).toBeVisible();
  await slow(nour.getByRole('region', { name: 'Chat' })).toBeHidden();
});
