import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');

async function signInAsAdmin(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Work email').fill('admin@corridor.local');
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Your profile' })).toBeVisible();
}

test('admin adds a person, suspends, reactivates and removes them', async ({ page }) => {
  const email = `e2e-${Date.now()}@corridor.local`;
  await signInAsAdmin(page);
  await page.getByRole('link', { name: 'People' }).click();

  await page.getByRole('button', { name: 'Add person' }).click();
  const add = page.getByRole('dialog', { name: 'Add person' });
  await add.getByLabel('Name').fill('Test Person');
  await add.getByLabel('Work email').fill(email);
  await add.getByLabel('First password').fill('Welcome-pass-1');
  await add.getByRole('button', { name: 'Add person' }).click();
  await expect(add).toBeHidden();

  await page.getByLabel('Search people').fill(email);
  await page.getByRole('button', { name: 'Edit Test Person' }).click();
  const sheet = page.getByRole('dialog', { name: 'Test Person' });
  await sheet.getByRole('button', { name: 'Suspend' }).click();
  await expect(sheet.getByText("Suspended. Test can't sign in.")).toBeVisible();
  await sheet.getByRole('button', { name: 'Reactivate' }).click();
  await expect(sheet.getByText('Active. Can sign in.')).toBeVisible();

  await sheet.getByRole('button', { name: 'Remove from Corridor' }).click();
  await sheet.getByRole('button', { name: 'Remove', exact: true }).click();
  await expect(sheet).toBeHidden();
  await expect(page.getByText('No people match these filters.')).toBeVisible();
});

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`in ${colorScheme} mode`, () => {
    test.use({ colorScheme });

    test('people and teams meet WCAG AA', async ({ page }) => {
      await signInAsAdmin(page);
      for (const [path, heading] of [
        ['/admin/users', 'People'],
        ['/admin/teams', 'Teams'],
      ]) {
        await page.goto(path);
        await expect(page.getByRole('heading', { name: heading })).toBeVisible();
        await page.waitForLoadState('networkidle');
        const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
        expect(results.violations).toEqual([]);
      }
    });
  });
}
