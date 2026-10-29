import { screen } from '@testing-library/react';
import { problem, stubFetch, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

beforeEach(resetSession);

it('restores the session from the refresh cookie after a reload', async () => {
  stubFetch([
    [200, tokenResponse('restored')],
    [200, tokenResponse('restored').user],
  ]);

  renderApp('/me');

  expect(await screen.findByRole('heading', { name: 'Your profile' })).toBeInTheDocument();
});

it('sends people without a session to sign in, without a session-ended notice', async () => {
  stubFetch([[401, problem('SESSION_ENDED')]]);

  renderApp('/me');

  expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});
