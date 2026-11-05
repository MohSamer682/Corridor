import { expect, test } from '@playwright/test';

// Runs against the real API: start the backend with the local profile, then set E2E_API=1.
test.skip(!process.env.E2E_API, 'needs the local backend on :8080');
test.setTimeout(300_000);
const slow = expect.configure({ timeout: 45_000 });

const card = (key: string, summary: string, column: string) => ({
  key,
  summary,
  status: column,
  type: 'Story',
  priority: 'High',
  assignee: 'Nour Hassan',
  storyPoints: 3,
  url: `https://corridor.atlassian.net/browse/${key}`,
});

// Jira itself is stubbed: the board the server would have cached from it, four minutes old.
const board = {
  projectKey: 'COR',
  siteUrl: 'https://corridor.atlassian.net',
  stale: true,
  columns: [
    { name: 'To Do', cards: [card('COR-4', 'Command center', 'To Do')] },
    {
      name: 'In Progress',
      cards: [card('COR-1', 'Punch clock shows a live timer', 'In Progress')],
    },
    { name: 'In Review', cards: [card('COR-2', 'Avatars sit on sofas', 'Code Review')] },
    { name: 'Done', cards: [card('COR-3', 'Town hall banner', 'Done')] },
  ],
};

test('the sprint board on a floor shows four columns, and says when Jira is unreachable', async ({
  page,
}) => {
  await page.route('**/api/v1/board', (route) =>
    route.fulfill({
      json: { ...board, syncedAt: new Date(Date.now() - 4 * 60_000).toISOString() },
    }),
  );
  await page.goto('/login');
  await page.getByLabel('Work email').fill('nour@corridor.local');
  await page.getByLabel('Password', { exact: true }).fill('Corridor-local-1');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.getByRole('button', { name: 'Enter Corridor HQ' }).click();
  await page
    .getByRole('navigation', { name: 'Floors' })
    .getByRole('link', { name: /Engineering/ })
    .click();
  await slow(page).toHaveURL(/\/office\/floors\//);

  await page.getByRole('button', { name: /^Sprint board/ }).click();
  const dialog = page.getByRole('dialog', { name: 'COR sprint board' });
  await expect(dialog.getByRole('region')).toHaveCount(4);
  await expect(dialog.getByRole('region', { name: 'In Review, 1 issue' })).toContainText(
    'Avatars sit on sofas',
  );
  await expect(
    dialog.getByText('Jira is unreachable. Showing the board from 4 minutes ago.'),
  ).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
});

test('the Jira webhook refuses calls without the shared secret', async ({ request }) => {
  const response = await request.post(
    'http://localhost:8080/api/v1/integrations/jira/webhook/00000000-0000-0000-0000-000000000000',
    { data: { webhookEvent: 'jira:issue_updated', issue: { key: 'COR-1' } } },
  );
  expect(response.status()).toBe(401);
});
