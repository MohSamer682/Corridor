import { vi } from 'vitest';

type StubbedResponse = [status: number, body: unknown];
type Call = { url: string; init: RequestInit };

let calls: Call[] = [];

// The top bar's punch clock asks on every signed-in page; tests that don't care get "not punched in".
const PUNCH = { punchedIn: false, since: null, lastOut: null, workedTodayMinutes: 0 };
const isPunchClock = (url: string, init: RequestInit) =>
  url === '/api/v1/me/punch' && (init.method ?? 'GET') === 'GET';
// The shell also asks for unread announcements and a live town hall on every page: none of either.
const shellResponse = (url: string, init: RequestInit) => {
  if ((init.method ?? 'GET') !== 'GET') return null;
  if (url === '/api/v1/announcements/unread') return json(200, []);
  if (url === '/api/v1/town-hall') return new Response(null, { status: 204 });
  return null;
};
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });

export function stubFetch(responses: StubbedResponse[], options: { delayMs?: number } = {}) {
  calls = [];
  const queue = [...responses];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      if (isPunchClock(url, init)) return json(200, PUNCH);
      const shell = shellResponse(url, init);
      if (shell) return shell;
      calls.push({ url, init });
      const next = queue.shift();
      if (!next) throw new Error(`Unexpected request to ${url}`);
      if (options.delayMs) await new Promise((resolve) => setTimeout(resolve, options.delayMs));
      const [status, body] = next;
      return new Response(JSON.stringify(body), {
        status,
        headers: {
          'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json',
        },
      });
    }),
  );
}

type Handler = (method: string, url: string, body: unknown) => StubbedResponse;

export function stubApi(handler: Handler, handlesShell = false) {
  calls = [];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
      // handlesShell: the test answers the punch clock, announcements and town hall itself.
      if (!handlesShell) {
        if (isPunchClock(url, init)) return json(200, PUNCH);
        const shell = shellResponse(url, init);
        if (shell) return shell;
      }
      calls.push({ url, init });
      const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined;
      const [status, payload] = handler(init.method ?? 'GET', url, body);
      return new Response(JSON.stringify(payload), {
        status,
        headers: {
          'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json',
        },
      });
    }),
  );
}

export function fetchCalls(): Call[] {
  return calls;
}

export function lastAuthHeader(): string | null {
  const last = calls.at(-1);
  return new Headers(last?.init.headers).get('Authorization');
}

export function problem(code: string, detail = 'Problem') {
  return { status: 401, code, detail };
}

export function tokenResponse(accessToken: string) {
  return {
    accessToken,
    expiresIn: 600,
    user: {
      id: '0192d3a4-0000-7000-8000-000000000001',
      email: 'nour@corridor.local',
      displayName: 'Nour',
      role: 'EMPLOYEE' as const,
      organizationName: 'Corridor Technologies',
    },
  };
}
