import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');

// Starts from the profile so sign-in returns there; the office has its own spec.
async function signIn(page: Page, password = 'Corridor-local-1') {
  await page.goto('/me');
  await page.getByLabel('Work email').fill('nour@corridor.local');
  await page.getByLabel('Password', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

test('signs in, survives a reload and signs out', async ({ page }) => {
  await signIn(page);
  await expect(page.getByRole('heading', { name: 'Your profile' })).toBeVisible();
  await expect(page.getByText('nour@corridor.local')).toBeVisible();

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Your profile' })).toBeVisible();

  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await expect(page).toHaveURL(/\/login$/);
});

test('shows the server message for a wrong password', async ({ page }) => {
  await signIn(page, 'Wrong-horse-1');
  await expect(page.getByRole('alert')).toHaveText('Email or password is incorrect.');
});

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`in ${colorScheme} mode`, () => {
    test.use({ colorScheme });

    test('sign-in and profile meet WCAG AA', async ({ page }) => {
      await page.goto('/login');
      expect(
        (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()).violations,
      ).toEqual([]);

      await signIn(page);
      await expect(page.getByText('nour@corridor.local')).toBeVisible();
      expect(
        (await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze()).violations,
      ).toEqual([]);
    });
  });
}
