import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');
// Without a GPU, Chromium draws WebGL in software, which is slow.
test.setTimeout(120_000);
const slow = expect.configure({ timeout: 45_000 });

async function signIn(page: Page) {
  await page.goto('/login');
  await page.getByLabel('Work email').fill('nour@corridor.local');
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await slow(page.getByRole('link', { name: 'Office' })).toHaveAttribute('aria-current', 'page');
}

test('walks from the campus into a building and onto a floor', async ({ page }) => {
  await signIn(page);
  await slow(page.getByRole('heading', { name: 'Your campus' })).toBeVisible();
  // Building labels follow the 3D models, so seeing them means the scene is running.
  await slow(page.getByText('3 floors')).toBeVisible();

  await page.getByRole('button', { name: 'Enter Corridor HQ' }).click();
  const floors = page.getByRole('navigation', { name: 'Floors' });
  await slow(floors.getByRole('link')).toHaveCount(4);
  await slow(page.getByText('Backend, frontend and DevOps', { exact: false })).toBeVisible({
    timeout: 30_000,
  });

  await floors.getByRole('link', { name: /Engineering/ }).click();
  await slow(page).toHaveURL(/\/office\/floors\//);
  await slow(page.getByRole('navigation', { name: 'Location' })).toContainText('L1 · Engineering');
  const cutaway = page.getByRole('button', { name: 'Cutaway' });
  await cutaway.click();
  await slow(cutaway).toHaveAttribute('aria-pressed', 'true');

  await page
    .getByRole('navigation', { name: 'Location' })
    .getByRole('button', { name: 'Back' })
    .click();
  await slow(page).toHaveURL(/\/office\/buildings\//);
});

test('explains when the browser has WebGL turned off', async ({ page }) => {
  await page.addInitScript(() => {
    const original = HTMLCanvasElement.prototype.getContext;
    HTMLCanvasElement.prototype.getContext = function (
      this: HTMLCanvasElement,
      type: string,
      ...rest: unknown[]
    ) {
      return type.startsWith('webgl') ? null : original.call(this, type as '2d', ...(rest as []));
    } as typeof original;
  });
  await signIn(page);

  await slow(
    page.getByText('The 3D office needs WebGL, which this browser has turned off.'),
  ).toBeVisible();
  await page.getByRole('link', { name: 'Profile' }).click();
  await slow(page.getByRole('heading', { name: 'Your profile' })).toBeVisible();
});

for (const colorScheme of ['light', 'dark'] as const) {
  test.describe(`in ${colorScheme} mode`, () => {
    test.use({ colorScheme });

    test('the office controls meet WCAG AA', async ({ page }) => {
      await signIn(page);
      await page.getByRole('button', { name: 'Enter Corridor HQ' }).click();
      await slow(page.getByRole('navigation', { name: 'Floors' })).toBeVisible();
      const results = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa'])
        .exclude('canvas')
        .analyze();
      expect(results.violations).toEqual([]);
    });
  });
}
