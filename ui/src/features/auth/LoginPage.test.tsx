import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { fetchCalls, problem, stubFetch, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';
import { useSessionStore } from './sessionStore';

const user = userEvent.setup();

beforeEach(resetSession);

async function fillValidCredentials() {
  await user.type(screen.getByLabelText('Work email'), 'nour@corridor.local');
  await user.type(screen.getByLabelText('Password'), 'Correct-horse-1');
}

it('shows field errors before calling the server', async () => {
  stubFetch([]);
  renderApp('/login');

  await user.click(screen.getByRole('button', { name: 'Sign in' }));

  expect(await screen.findByText('Enter your work email.')).toBeInTheDocument();
  expect(screen.getByText('Use 8 to 128 characters.')).toBeInTheDocument();
  expect(screen.getByLabelText('Work email')).toHaveAttribute('aria-invalid', 'true');
  expect(fetchCalls()).toHaveLength(0);
});

it('shows the server message when credentials are wrong', async () => {
  stubFetch([[401, problem('INVALID_CREDENTIALS', 'Email or password is incorrect.')]]);
  renderApp('/login');

  await user.type(screen.getByLabelText('Work email'), 'nour@corridor.local');
  await user.type(screen.getByLabelText('Password'), 'Wrong-horse-1');
  await user.click(screen.getByRole('button', { name: 'Sign in' }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
});

it('disables the button while signing in', async () => {
  stubFetch([[200, tokenResponse('t')]], { delayMs: 50 });
  renderApp('/login');

  await fillValidCredentials();
  await user.click(screen.getByRole('button', { name: 'Sign in' }));

  expect(screen.getByRole('button', { name: 'Signing in…' })).toBeDisabled();
});

it('opens the profile after signing in', async () => {
  stubFetch([
    [200, tokenResponse('t')],
    [200, tokenResponse('t').user],
  ]);
  renderApp('/login');

  await fillValidCredentials();
  await user.click(screen.getByRole('button', { name: 'Sign in' }));

  expect(await screen.findByRole('heading', { name: 'Your profile' })).toBeInTheDocument();
  expect(useSessionStore.getState().accessToken).toBe('t');
});

it('tells people when their session ended', () => {
  useSessionStore.setState({ sessionEnded: true });
  renderApp('/login');

  expect(screen.getByRole('status')).toHaveTextContent(
    'Your session ended. Sign in again to continue.',
  );
});

it('lets people reveal the password', async () => {
  renderApp('/login');

  await user.click(screen.getByRole('button', { name: 'Show password' }));

  expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'text');
  expect(screen.getByRole('button', { name: 'Hide password' })).toBeInTheDocument();
});
