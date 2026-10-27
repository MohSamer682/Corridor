import { expect, test } from '@playwright/test';

test('shows the not found page for an unknown address', async ({ page }) => {
  await page.goto('/nope');
  await expect(page.getByRole('heading', { name: 'This page doesn’t exist' })).toBeVisible();
});

test.describe('in dark mode', () => {
  test.use({ colorScheme: 'dark' });

  test('uses the dark page background', async ({ page }) => {
    await page.goto('/nope');
    const background = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
    expect(background).toBe('rgb(14, 21, 33)');
  });
});
