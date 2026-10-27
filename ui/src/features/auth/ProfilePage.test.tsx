import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fetchCalls, stubFetch, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';
import { useSessionStore } from './sessionStore';

const user = userEvent.setup();
const me = {
  ...tokenResponse('t').user,
  displayName: 'Nour Hassan',
  organizationName: 'Corridor Technologies',
};

beforeEach(() => {
  resetSession();
  useSessionStore.getState().signIn({ ...tokenResponse('t'), user: me });
});

it('shows a loading card, then the signed-in user', async () => {
  stubFetch([[200, me]], { delayMs: 20 });
  renderApp('/me');

  expect(screen.getByLabelText('Loading your profile')).toHaveAttribute('aria-busy', 'true');
  expect(await screen.findByText('nour@corridor.local')).toBeInTheDocument();
  expect(screen.getAllByText('Member')).not.toHaveLength(0);
  expect(screen.getAllByText('Corridor Technologies')).not.toHaveLength(0);
  expect(screen.getAllByText('NH')).not.toHaveLength(0);
});

it('offers to try again when the profile does not load', async () => {
  stubFetch([
    [500, { status: 500, code: 'INTERNAL_ERROR', detail: 'Something went wrong. Try again.' }],
    [200, me],
  ]);
  renderApp('/me');

  expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't load your profile.");
  await user.click(screen.getByRole('button', { name: 'Try again' }));

  expect(await screen.findByText('nour@corridor.local')).toBeInTheDocument();
});

it('signs out and returns to the sign-in screen', async () => {
  stubFetch([
    [200, me],
    [200, { id: me.id, status: 'SIGNED_OUT' }],
  ]);
  renderApp('/me');

  await user.click(await screen.findByRole('button', { name: 'Sign out' }));

  expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  expect(fetchCalls().at(1)?.url).toBe('/api/v1/auth/logout');
  expect(useSessionStore.getState().accessToken).toBeNull();
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});
