import { useEffect, useState } from 'react';

/** The current time, ticking every `ms` while `running`; frozen otherwise, so idle screens don't re-render. */
export function useTicker(running: boolean, ms = 1000) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!running) return undefined;
    const timer = setInterval(() => setNow(Date.now()), ms);
    return () => clearInterval(timer);
  }, [running, ms]);
  return now;
}

/** 8_052_000 → "2:14:12": hours, then minutes and seconds padded. */
export function stopwatch(ms: number) {
  const total = Math.max(0, Math.floor(ms / 1000));
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${Math.floor(total / 3600)}:${pad(Math.floor(total / 60) % 60)}:${pad(total % 60)}`;
}
