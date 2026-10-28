import { useSessionStore } from '../../features/auth/sessionStore';
import {
  fetchCalls,
  lastAuthHeader,
  problem,
  stubFetch,
  tokenResponse,
} from '../../test/fetchStub';
import { apiClient } from './apiClient';

beforeEach(() => {
  useSessionStore.getState().signIn(tokenResponse('old'));
});

it('sends the access token', async () => {
  stubFetch([[200, { ok: true }]]);

  await apiClient('/api/v1/me');

  expect(lastAuthHeader()).toBe('Bearer old');
});

it('refreshes once and retries when the access token expired', async () => {
  stubFetch([
    [401, problem('UNAUTHENTICATED')],
    [200, tokenResponse('new')],
    [200, { ok: true }],
  ]);

  await expect(apiClient('/api/v1/me')).resolves.toEqual({ ok: true });

  expect(fetchCalls().map((call) => call.url)).toEqual([
    '/api/v1/me',
    '/api/v1/auth/refresh',
    '/api/v1/me',
  ]);
  expect(lastAuthHeader()).toBe('Bearer new');
  expect(fetchCalls().at(1)?.init.credentials).toBe('include');
});

it('refreshes once when two requests get 401 together', async () => {
  stubFetch([
    [401, problem('UNAUTHENTICATED')],
    [401, problem('UNAUTHENTICATED')],
    [200, tokenResponse('new')],
    [200, {}],
    [200, {}],
  ]);

  await Promise.all([apiClient('/api/v1/me'), apiClient('/api/v1/me')]);

  expect(fetchCalls().filter((call) => call.url === '/api/v1/auth/refresh')).toHaveLength(1);
});

it('signs out after a failed refresh without retrying again', async () => {
  stubFetch([
    [401, problem('UNAUTHENTICATED')],
    [401, problem('SESSION_ENDED', 'Your session has ended. Sign in again.')],
  ]);

  await expect(apiClient('/api/v1/me')).rejects.toMatchObject({
    status: 401,
    code: 'SESSION_ENDED',
  });

  expect(fetchCalls()).toHaveLength(2);
  expect(useSessionStore.getState().accessToken).toBeNull();
  expect(useSessionStore.getState().sessionEnded).toBe(true);
});

it('turns an error response into a ProblemError with the server copy', async () => {
  stubFetch([
    [
      422,
      { status: 422, code: 'VALIDATION_FAILED', detail: 'Some fields need attention.', errors: [] },
    ],
  ]);

  await expect(
    apiClient('/api/v1/auth/login', { method: 'POST', body: '{}' }),
  ).rejects.toMatchObject({
    status: 422,
    code: 'VALIDATION_FAILED',
    detail: 'Some fields need attention.',
  });
});
