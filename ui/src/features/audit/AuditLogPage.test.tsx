import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSessionStore } from '../auth/sessionStore';
import { fetchCalls, stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const user = userEvent.setup();

const suspension = {
  id: 'e2',
  action: 'user.suspended',
  createdAt: new Date().toISOString(),
  actorId: 'a1',
  targetType: 'user',
  targetId: 'u1',
  details: { name: 'Omar Adel', actorName: 'Muhammed Samer', actorRole: 'ADMIN' },
};
const failedSignIn = {
  id: 'e1',
  action: 'auth.login_failed',
  createdAt: new Date(Date.now() - 86_400_000).toISOString(),
  targetType: 'user',
  details: { email: 'ceo@corridor.local', reason: 'unknown_email', ip: '196.204.7.40' },
};
const intact = { intact: true, checked: 2 };
const people = { items: [], page: 0, size: 100, totalItems: 0, totalPages: 0 };

function signInAs(role: 'ADMIN' | 'HR' | 'MANAGER') {
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role } });
}

beforeEach(() => {
  resetSession();
  signInAs('ADMIN');
});

it('shows events grouped by day with who did what', async () => {
  stubApi((_method, url) => {
    if (url.includes('/verification')) return [200, intact];
    if (url.includes('/users')) return [200, people];
    return [200, { items: [suspension, failedSignIn], nextCursor: null }];
  });
  renderApp('/admin/audit');

  const table = await screen.findByRole('table');
  expect(within(table).getByText(/^Today/)).toBeInTheDocument();
  expect(within(table).getByText(/^Yesterday/)).toBeInTheDocument();
  const row = within(table).getByText('Omar Adel').closest('tr') as HTMLElement;
  expect(row).toHaveTextContent('Suspended Omar Adel');
  expect(row).toHaveTextContent('Muhammed Samer');
  expect(within(table).getByText('No account with this email')).toBeInTheDocument();
  expect(await screen.findByRole('status')).toHaveTextContent('Log intact');
});

it('loads older events with the cursor', async () => {
  stubApi((_method, url) => {
    if (url.includes('/verification')) return [200, intact];
    if (url.includes('/users')) return [200, people];
    if (url.includes('cursor=c1')) return [200, { items: [failedSignIn], nextCursor: null }];
    return [200, { items: [suspension], nextCursor: 'c1' }];
  });
  renderApp('/admin/audit');

  await user.click(await screen.findByRole('button', { name: 'Load older events' }));

  expect((await screen.findAllByText('ceo@corridor.local')).length).toBeGreaterThan(0);
  expect(screen.queryByRole('button', { name: 'Load older events' })).not.toBeInTheDocument();
});

it('filters by failed sign-ins', async () => {
  stubApi((_method, url) => {
    if (url.includes('/verification')) return [200, intact];
    if (url.includes('/users')) return [200, people];
    return [200, { items: [], nextCursor: null }];
  });
  renderApp('/admin/audit');

  await user.selectOptions(await screen.findByLabelText('Action'), 'sign-in');

  expect(await screen.findByText('No events match these filters.')).toBeInTheDocument();
  expect(fetchCalls().some((call) => call.url.includes('action=sign-in'))).toBe(true);
});

it('warns when the log was altered', async () => {
  stubApi((_method, url) => {
    if (url.includes('/verification'))
      return [
        200,
        { intact: false, checked: 2, firstAlteredId: 'e1', firstAlteredAt: '2026-10-27T14:02:00Z' },
      ];
    if (url.includes('/users')) return [200, people];
    return [200, { items: [], nextCursor: null }];
  });
  renderApp('/admin/audit');

  expect(await screen.findByRole('alert')).toHaveTextContent('was changed outside Corridor');
  expect(screen.getByText('Log altered')).toBeInTheDocument();
});

it('offers a retry when the log cannot load', async () => {
  stubApi((_method, url) => {
    if (url.includes('/verification')) return [200, intact];
    if (url.includes('/users')) return [200, people];
    return [500, { status: 500, code: 'INTERNAL_ERROR', detail: 'Something went wrong.' }];
  });
  renderApp('/admin/audit');

  expect(
    await screen.findByText("We couldn't load the audit log. Check your connection and try again."),
  ).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
});

it('lets HR open the log from the nav', async () => {
  signInAs('HR');
  stubApi((_method, url) => {
    if (url.includes('/verification')) return [200, intact];
    if (url.includes('/users')) return [200, people];
    return [200, { items: [], nextCursor: null }];
  });
  renderApp('/admin/audit');

  expect(await screen.findByRole('link', { name: 'Audit log' })).toHaveAttribute(
    'aria-current',
    'page',
  );
});

it('hides the log from managers', async () => {
  signInAs('MANAGER');
  stubApi(() => [200, {}]);
  renderApp('/admin/audit');

  expect(await screen.findByText('This page doesn’t exist')).toBeInTheDocument();
  expect(screen.queryByRole('link', { name: 'Audit log' })).not.toBeInTheDocument();
});
