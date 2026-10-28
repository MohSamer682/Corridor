import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSessionStore } from '../auth/sessionStore';
import { fetchCalls, stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const user = userEvent.setup();
const product = { id: 't1', name: 'Product', color: '#2F7D6D', memberCount: 2, version: 0 };

function page(items: unknown[]) {
  return { items, page: 0, size: 100, totalItems: items.length, totalPages: 1 };
}

beforeEach(() => {
  resetSession();
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role: 'ADMIN' } });
});

it('adds a team with the chosen color', async () => {
  let teams = [product];
  stubApi((method, _url, body) => {
    if (method === 'POST') {
      const created = { id: 't2', memberCount: 0, version: 0, ...(body as object) };
      teams = [...teams, created as typeof product];
      return [201, created];
    }
    return [200, page(teams)];
  });
  renderApp('/admin/teams');

  await user.type(await screen.findByLabelText('Team name'), 'Platform');
  await user.click(screen.getByRole('radio', { name: 'Brass' }));
  await user.click(screen.getByRole('button', { name: 'Add team' }));

  expect(await screen.findByText('Platform')).toBeInTheDocument();
  const post = fetchCalls().find((call) => call.init.method === 'POST');
  expect(JSON.parse(String(post?.init.body))).toEqual({ name: 'Platform', color: '#B08A4E' });
});

it('blocks deleting a team that still has members with the server copy', async () => {
  stubApi((method) =>
    method === 'DELETE'
      ? [
          409,
          { status: 409, code: 'CONFLICT', detail: "Move the team's members before deleting it." },
        ]
      : [200, page([product])],
  );
  renderApp('/admin/teams');

  const row = (await screen.findByText('Product')).closest('tr') as HTMLElement;
  await user.click(within(row).getByRole('button', { name: 'Delete Product' }));

  expect(await screen.findByRole('alert')).toHaveTextContent(
    "Move the team's members before deleting it.",
  );
});
