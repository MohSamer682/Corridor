import { expect, test, type Browser, type Page } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');

async function signIn(browser: Browser, email: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/login');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Profile' }).click();
  return page;
}

test('a broadcast shows up for someone else, and a town hall invites them', async ({ browser }) => {
  const salma = await signIn(browser, 'manager@corridor.local');
  const omar = await signIn(browser, 'omar@corridor.local');
  const text = `Release freeze starts at 5 pm (${Date.now() % 100_000}).`;

  await salma.getByRole('button', { name: 'Broadcast' }).click();
  const sheet = salma.getByRole('dialog', { name: 'Broadcast' });
  await sheet.getByLabel('Message').fill(text);
  await sheet.getByRole('button', { name: 'Send' }).click();
  await expect(sheet.getByText(/^Sent to \d+ (person|people)\./)).toBeVisible();

  // Omar is on his profile, not on a floor, so the banner arrives on the next refresh (20 s at most).
  const banner = omar.getByRole('region', { name: 'Announcement' });
  await expect(banner).toContainText(text, { timeout: 30_000 });

  await sheet.getByRole('button', { name: 'Call a town hall' }).click();
  await expect(sheet.getByRole('button', { name: 'End the town hall' })).toBeVisible();
  // A page in the background polls less eagerly; opening it again shows the town hall at once.
  await omar.reload();
  await expect(omar.getByRole('region', { name: 'Town hall' })).toContainText('All-hands', {
    timeout: 30_000,
  });
  await sheet.getByRole('button', { name: 'End the town hall' }).click();
  await expect(sheet.getByRole('button', { name: 'Call a town hall' })).toBeVisible();
});
