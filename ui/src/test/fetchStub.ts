import { vi } from 'vitest';

type StubbedResponse = [status: number, body: unknown];
type Call = { url: string; init: RequestInit };

let calls: Call[] = [];

export function stubFetch(responses: StubbedResponse[], options: { delayMs?: number } = {}) {
  calls = [];
  const queue = [...responses];
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit = {}) => {
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
