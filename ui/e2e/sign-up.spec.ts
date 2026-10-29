import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');

test('someone signs up, waits for approval, and signs in once approved', async ({ page }) => {
  const email = `signup-${Date.now()}@corridor.local`;
  await page.goto('/login');
  await page.getByRole('link', { name: 'Create an account' }).click();
  await page.getByLabel('Full name').fill('Zeinab Ali');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Corridor-pass-1');
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('heading', { name: 'Request sent' })).toBeVisible();

  await page.getByRole('link', { name: 'Back to sign in' }).click();
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Corridor-pass-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('status')).toHaveText(
    'Your account is waiting for approval. HR will let you know.',
  );

  await page.goto('/admin/users');
  await page.getByLabel('Work email').fill('admin@corridor.local');
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('tab', { name: /Requests/ }).click();
  const request = page
    .getByText(email)
    .locator('xpath=ancestor::div[contains(@class, "border-b")][1]');
  await request.getByRole('button', { name: 'Approve Zeinab Ali' }).click();
  await request.getByRole('button', { name: 'Approve Zeinab', exact: true }).click();
  await expect(page.getByText(email)).toHaveCount(0);

  await page.goto('/me');
  await page.getByRole('button', { name: 'Sign out' }).click();
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Corridor-pass-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  // Sign-out came from the profile, so signing back in returns there.
  await expect(page.getByRole('heading', { name: 'Your profile' })).toBeVisible();
});

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`in ${colorScheme} mode`, () => {
    test.use({ colorScheme });

    test('sign-up meets WCAG AA', async ({ page }) => {
      await page.goto('/sign-up');
      await expect(page.getByRole('heading', { name: 'Create an account' })).toBeVisible();
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
      expect(results.violations).toEqual([]);
    });
  });
}
