import { useSessionStore, type TokenResponse } from '../../features/auth/sessionStore';
import { ProblemError } from './problem';

const AUTH_PATH = '/api/v1/auth/';
const REFRESH_PATH = '/api/v1/auth/refresh';

let refreshing: Promise<void> | null = null;

export async function apiClient<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await send(path, init);
  if (response.status === 401 && !path.startsWith(AUTH_PATH)) {
    await refreshOnce();
    return parse<T>(await send(path, init));
  }
  return parse<T>(response);
}

export function refreshOnce(): Promise<void> {
  refreshing ??= navigator.locks.request('corridor-refresh', refresh).finally(() => {
    refreshing = null;
  });
  return refreshing;
}

async function refresh(): Promise<void> {
  const session = useSessionStore.getState();
  try {
    session.signIn(await parse<TokenResponse>(await send(REFRESH_PATH, { method: 'POST' })));
  } catch (error) {
    // Only someone who was signed in has a session that "ended"; a first visit just has none.
    session.signOut(session.accessToken !== null);
    throw error;
  }
}

function send(path: string, init: RequestInit): Promise<Response> {
  const headers = new Headers(init.headers);
  const token = useSessionStore.getState().accessToken;
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (typeof init.body === 'string') headers.set('Content-Type', 'application/json');
  return fetch(path, {
    ...init,
    headers,
    credentials: path.startsWith(AUTH_PATH) ? 'include' : 'same-origin',
  });
}

async function parse<T>(response: Response): Promise<T> {
  if (!response.ok) throw await ProblemError.from(response);
  return (await response.json()) as T;
}
