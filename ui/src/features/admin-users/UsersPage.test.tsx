import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSessionStore } from '../auth/sessionStore';
import { fetchCalls, stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const user = userEvent.setup();

const team = { id: 't1', name: 'Product', color: '#2F7D6D', memberCount: 1, version: 0 };
const omar = {
  id: 'u2',
  email: 'omar@corridor.local',
  displayName: 'Omar Adel',
  role: 'EMPLOYEE',
  status: 'SUSPENDED',
  teamId: null,
  teamName: null,
};
const omarView = { ...omar, organizationName: 'Corridor Technologies', version: 4, jobTitle: null };

function page(items: unknown[], totalItems = items.length, pageNumber = 0) {
  return { items, page: pageNumber, size: 20, totalItems, totalPages: Math.ceil(totalItems / 20) };
}

function signInAs(role: 'ADMIN' | 'EMPLOYEE') {
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role } });
}

beforeEach(() => {
  resetSession();
  signInAs('ADMIN');
});

it('hides admin screens from employees', async () => {
  resetSession();
  signInAs('EMPLOYEE');
  stubApi(() => [200, page([])]);

  renderApp('/admin/users');

  expect(
    await screen.findByRole('heading', { name: 'This page doesn’t exist' }),
  ).toBeInTheDocument();
  expect(fetchCalls()).toHaveLength(0);
});

it('filters people by status and search and pages through them', async () => {
  stubApi((_method, url) => {
    if (url.startsWith('/api/v1/teams')) return [200, page([team])];
    if (url.includes('page=1')) return [200, page([omar], 21, 1)];
    return [200, page([omar], 21)];
  });
  renderApp('/admin/users');

  expect(await screen.findByText('Omar Adel')).toBeInTheDocument();
  await user.selectOptions(screen.getByLabelText('Status'), 'SUSPENDED');
  await user.type(screen.getByLabelText('Search people'), 'omar');
  await user.click(screen.getByRole('button', { name: 'Next' }));

  await waitFor(() => {
    const last =
      fetchCalls()
        .filter((call) => call.url.startsWith('/api/v1/users'))
        .at(-1)?.url ?? '';
    expect(last).toContain('status=SUSPENDED');
    expect(last).toContain('query=omar');
    expect(last).toContain('page=1');
  });
  expect(await screen.findByText('21–21 of 21 people')).toBeInTheDocument();
});

it('shows a message when no one matches the filters', async () => {
  stubApi((_method, url) => [200, url.startsWith('/api/v1/teams') ? page([team]) : page([])]);
  renderApp('/admin/users');

  expect(await screen.findByText('No people match these filters.')).toBeInTheDocument();
});

it('adds a person and shows them in the list', async () => {
  let added = false;
  stubApi((method, url) => {
    if (url.startsWith('/api/v1/teams')) return [200, page([team])];
    if (method === 'POST') {
      added = true;
      return [201, { ...omarView, displayName: 'Nour Hassan', email: 'nour@corridor.local' }];
    }
    return [200, page(added ? [{ ...omar, displayName: 'Nour Hassan' }] : [])];
  });
  renderApp('/admin/users');

  await user.click(await screen.findByRole('button', { name: 'Add person' }));
  const sheet = await screen.findByRole('dialog', { name: 'Add person' });
  await user.type(within(sheet).getByLabelText('Name'), 'Nour Hassan');
  await user.type(within(sheet).getByLabelText('Work email'), 'nour@corridor.local');
  await user.type(within(sheet).getByLabelText('First password'), 'Welcome-pass-1');
  await user.click(within(sheet).getByRole('button', { name: 'Add person' }));

  expect(await screen.findByText('Nour Hassan')).toBeInTheDocument();
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
});

it('asks before removing someone and removes them on confirm', async () => {
  stubApi((method, url) => {
    if (url.startsWith('/api/v1/teams')) return [200, page([team])];
    if (method === 'DELETE') return [200, { id: 'u2', status: 'REMOVED' }];
    if (url === '/api/v1/users/u2') return [200, omarView];
    return [200, page([omar])];
  });
  renderApp('/admin/users');

  await user.click(await screen.findByRole('button', { name: 'Edit Omar Adel' }));
  const sheet = await screen.findByRole('dialog', { name: 'Omar Adel' });
  await user.click(within(sheet).getByRole('button', { name: 'Remove from Corridor' }));
  expect(within(sheet).getByRole('group', { name: 'Confirm removal' })).toHaveTextContent(
    "Remove Omar Adel? They lose access right away, and this can't be undone.",
  );
  await user.click(within(sheet).getByRole('button', { name: 'Remove' }));

  await waitFor(() =>
    expect(fetchCalls().some((call) => call.init.method === 'DELETE')).toBe(true),
  );
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
});

it('shows the reload message when someone else changed the user', async () => {
  stubApi((method, url) => {
    if (url.startsWith('/api/v1/teams')) return [200, page([team])];
    if (method === 'PATCH')
      return [
        409,
        {
          status: 409,
          code: 'CONFLICT',
          detail: 'Someone else changed this user. Reload and try again.',
        },
      ];
    if (url === '/api/v1/users/u2') return [200, omarView];
    return [200, page([omar])];
  });
  renderApp('/admin/users');

  await user.click(await screen.findByRole('button', { name: 'Edit Omar Adel' }));
  const sheet = await screen.findByRole('dialog', { name: 'Omar Adel' });
  await user.click(await within(sheet).findByRole('button', { name: 'Save changes' }));

  expect(await within(sheet).findByRole('alert')).toHaveTextContent(
    'Someone else changed this user. Reload and try again.',
  );
  const patch = fetchCalls().find((call) => call.init.method === 'PATCH');
  expect(new Headers(patch?.init.headers).get('If-Match')).toBe('"4"');
});

it('returns focus to the edit button when the sheet closes', async () => {
  stubApi((_method, url) => {
    if (url.startsWith('/api/v1/teams')) return [200, page([team])];
    if (url === '/api/v1/users/u2') return [200, omarView];
    return [200, page([omar])];
  });
  renderApp('/admin/users');

  const edit = await screen.findByRole('button', { name: 'Edit Omar Adel' });
  await user.click(edit);
  await screen.findByRole('dialog', { name: 'Omar Adel' });
  await user.keyboard('{Escape}');

  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(edit).toHaveFocus();
});
