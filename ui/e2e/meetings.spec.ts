import { expect, test, type Page } from '@playwright/test';

// Runs against the real API and LiveKit from compose.yaml: start the backend with the local profile, then set
// E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');
test.setTimeout(180_000);

async function signIn(page: Page, email: string) {
  await page.goto('/login');
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('link', { name: 'Meetings' }).click();
  await expect(page.getByRole('heading', { name: 'Meetings', exact: true })).toBeVisible();
}

async function book(page: Page, title: string, start: Date) {
  await page.getByRole('button', { name: 'Schedule a meeting' }).click();
  const sheet = page.getByRole('dialog', { name: 'Schedule a meeting' });
  await sheet.getByLabel('Title').fill(title);
  await sheet.getByLabel('Date').fill(start.toLocaleDateString('en-CA'));
  const pad = (n: number) => String(n).padStart(2, '0');
  await sheet.getByLabel('Start').fill(`${pad(start.getHours())}:${pad(start.getMinutes())}`);
  await sheet.getByLabel('Room').selectOption({ label: 'Luxor Room' });
  await sheet.getByRole('checkbox', { name: /Omar Adel/ }).check();
  const answered = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/meetings') && r.request().method() === 'POST',
  );
  await sheet.getByRole('button', { name: 'Schedule' }).click();
  await answered;
  return sheet;
}

test('books a room, refuses the same slot, meets now and joins over LiveKit', async ({
  page,
  browser,
}) => {
  // A fresh quarter hour soon enough that Join is open, unique per run in minutes since the epoch.
  const start = new Date(Date.now() + 5 * 60_000);
  start.setMinutes(Math.ceil(start.getMinutes() / 15) * 15, 0, 0);
  const title = `Release planning ${Date.now() % 100_000}`;

  await signIn(page, 'nour@corridor.local');
  let sheet = await book(page, title, start);
  // An earlier run may hold the slot; then this run's own booking is the clash to check.
  if (
    await sheet
      .getByText(/is booked then for/)
      .isVisible()
      .catch(() => false)
  ) {
    await sheet.getByRole('button', { name: 'Cancel' }).click();
  } else {
    await expect(sheet).toBeHidden();
  }

  sheet = await book(page, 'Roadmap check-in', start);
  await expect(
    sheet.getByText(/^The Luxor Room is booked then for “.+”\. Pick another room or time\.$/),
  ).toBeVisible();
  await sheet.getByRole('button', { name: 'Cancel' }).click();

  // Meet now: a room booked from this moment, live straight away, and the organizer is in.
  const now = `Quick sync ${Date.now() % 100_000}`;
  await page.getByRole('button', { name: 'Schedule a meeting' }).click();
  const quick = page.getByRole('dialog', { name: 'Schedule a meeting' });
  await quick.getByLabel('Title').fill(now);
  await quick.getByLabel('Room').selectOption({ label: 'Siwa Room' });
  await quick.getByRole('checkbox', { name: /Omar Adel/ }).check();
  await quick.getByRole('button', { name: 'Meet now' }).click();
  await expect(page.getByRole('region', { name: 'Meeting' })).toBeVisible();

  const omar = await (await browser.newContext()).newPage();
  await signIn(omar, 'omar@corridor.local');
  await omar.getByRole('button', { name: `Join ${now}` }).click();
  await expect(omar.getByRole('region', { name: 'Meeting' })).toBeVisible();
  await expect(page.getByRole('group', { name: /^Omar.*, connected$/ })).toBeVisible({
    timeout: 60_000,
  });
});
