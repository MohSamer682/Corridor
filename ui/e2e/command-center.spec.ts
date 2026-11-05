import { expect, test, type Page } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');

async function signIn(page: Page, email: string) {
  await page.goto('/login');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).toHaveURL(/\/office/);
}

test('a manager opens the Command Center', async ({ page }) => {
  await signIn(page, 'manager@corridor.local');
  await page.getByRole('link', { name: 'Command Center' }).click();

  await expect(page.getByRole('heading', { name: 'Command Center' })).toBeVisible();
  await expect(page.getByRole('list', { name: 'Right now' }).getByRole('listitem')).toHaveCount(6);
  await expect(page.getByRole('region', { name: 'Floors' }).getByText(/Engineering/)).toBeVisible();
  await expect(page.getByRole('table', { name: 'Teams' })).toBeVisible();
});

test('an employee is sent back to the office', async ({ page }) => {
  await signIn(page, 'nour@corridor.local');
  await expect(page.getByRole('link', { name: 'Command Center' })).toHaveCount(0);

  await page.goto('/command-center');
  await expect(page).toHaveURL(/\/office$/);
});
