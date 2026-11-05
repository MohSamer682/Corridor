import { expect, test } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');

test('punches in, survives a reload, punches out and sees the day', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Work email').fill('omar@corridor.local');
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Attendance' }).click();
  await expect(page.getByRole('heading', { name: 'My attendance' })).toBeVisible();

  // A run that stopped half way may have left Omar punched in.
  const bar = page.getByRole('banner');
  const out = bar.getByRole('button', { name: 'Punch out' });
  if (await out.isVisible().catch(() => false)) await out.click();
  await bar.getByRole('button', { name: 'Punch in' }).click();
  await expect(bar.getByText(/^In since \d\d:\d\d$/)).toBeVisible();

  await page.reload();
  await expect(bar.getByText(/^In since \d\d:\d\d$/)).toBeVisible();
  await expect(page.getByText(/^You’re in since \d\d:\d\d\.$/)).toBeVisible();
  await bar.getByRole('button', { name: 'Punch out' }).click();

  await expect(bar.getByRole('status')).toContainText(/Punched out at \d\d:\d\d\./);
  await expect(bar.getByRole('status')).toContainText(/You worked .+ today\.|under a minute/);
  await expect(bar.getByRole('button', { name: 'Punch in' })).toBeVisible();
});

test('lets HR see the whole company', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Work email').fill('admin@corridor.local');
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Attendance' }).click();

  await expect(page.getByRole('heading', { name: 'Team attendance' })).toBeVisible();
  await expect(page.getByRole('cell', { name: /Omar Adel/ })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Team' })).toBeVisible();
});
