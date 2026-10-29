import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');

async function signInAsAdmin(page: Page) {
  // Start from the profile so sign-in returns there and skips the 3D office.
  await page.goto('/me');
  await page.getByLabel('Work email').fill('admin@corridor.local');
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByRole('heading', { name: 'Your profile' })).toBeVisible();
}

test('a suspension and a failed sign-in show up in the audit log', async ({ page }) => {
  const run = Date.now();
  const email = `e2e-audit-${run}@corridor.local`;
  const name = `Audit ${run}`;
  await page.goto('/login');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Not-the-password-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText('Email or password is incorrect.')).toBeVisible();

  await signInAsAdmin(page);
  await page.getByRole('link', { name: 'People' }).click();
  await page.getByRole('button', { name: 'Add person' }).click();
  const add = page.getByRole('dialog', { name: 'Add person' });
  await add.getByLabel('Name').fill(name);
  await add.getByLabel('Work email').fill(email);
  await add.getByLabel('First password').fill('Welcome-pass-1');
  await add.getByRole('button', { name: 'Add person' }).click();
  await expect(add).toBeHidden();
  await page.getByLabel('Search people').fill(email);
  await page.getByRole('button', { name: `Edit ${name}` }).click();
  const sheet = page.getByRole('dialog', { name });
  await sheet.getByRole('button', { name: 'Suspend' }).click();
  await expect(sheet.getByText("Suspended. Audit can't sign in.")).toBeVisible();
  await page.keyboard.press('Escape');

  await page.getByRole('link', { name: 'Audit log' }).click();
  const log = page.getByRole('table');
  await expect(log.getByRole('row', { name: new RegExp(`Suspended ${name}`) })).toBeVisible();
  await expect(log.getByRole('row', { name: new RegExp(`Added ${name}`) })).toBeVisible();

  await page.getByLabel('Action').selectOption('sign-in');
  await expect(log.getByRole('row', { name: new RegExp(email) })).toContainText(
    'No account with this email',
  );
  await expect(page.getByRole('status')).toContainText('Log intact');
});

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`in ${colorScheme} mode`, () => {
    test.use({ colorScheme });

    test('the audit log meets WCAG AA', async ({ page }) => {
      await signInAsAdmin(page);
      await page.goto('/admin/audit');
      await expect(page.getByRole('heading', { name: 'Audit log' })).toBeVisible();
      await page.waitForLoadState('networkidle');
      const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
      expect(results.violations).toEqual([]);
    });
  });
}
