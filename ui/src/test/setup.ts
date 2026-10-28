import '@testing-library/jest-dom/vitest';

// jsdom has no Web Locks; one in-memory queue is enough to make concurrent callers wait their turn.
if (!('locks' in navigator)) {
  let tail: Promise<unknown> = Promise.resolve();
  Object.defineProperty(navigator, 'locks', {
    value: {
      request: (_name: string, callback: () => Promise<unknown>) => {
        const run = tail.then(callback);
        tail = run.catch(() => undefined);
        return run;
      },
    },
  });
}

afterEach(() => {
  vi.unstubAllGlobals();
});
