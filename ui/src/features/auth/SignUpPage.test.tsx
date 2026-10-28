import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fetchCalls, stubFetch } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const user = userEvent.setup();

beforeEach(resetSession);

it('creates an account and shows the request was sent', async () => {
  stubFetch([[202, { id: null, status: 'PENDING' }]]);
  renderApp('/sign-up');

  await user.type(screen.getByLabelText('Full name'), 'Zeinab Ali');
  await user.type(screen.getByLabelText('Work email'), 'zeinab@corridor.local');
  await user.type(screen.getByLabelText('Password', { exact: true }), 'Corridor-pass-1');
  await user.click(screen.getByRole('button', { name: 'Create account' }));

  expect(await screen.findByRole('heading', { name: 'Request sent' })).toBeInTheDocument();
  expect(fetchCalls()[0]?.url).toBe('/api/v1/auth/registrations');
  expect(screen.getByRole('link', { name: 'Back to sign in' })).toHaveAttribute('href', '/login');
});

it('checks the fields before sending', async () => {
  stubFetch([]);
  renderApp('/sign-up');

  await user.click(screen.getByRole('button', { name: 'Create account' }));

  expect(await screen.findByText('Use 2 to 80 characters.')).toBeInTheDocument();
  expect(fetchCalls()).toHaveLength(0);
});

it('is reachable from the sign-in screen', async () => {
  renderApp('/login');

  await user.click(screen.getByRole('link', { name: 'Create an account' }));

  expect(await screen.findByRole('heading', { name: 'Create an account' })).toBeInTheDocument();
});
