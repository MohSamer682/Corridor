import { useEffect, useState } from 'react';
import { clock, duration } from './format';
import { usePunch, useMyPunch } from './useAttendance';

const button =
  'h-9 cursor-pointer rounded-[9px] px-3.5 text-sm font-semibold disabled:cursor-default disabled:opacity-60';

/** Top-bar punch clock: says where you stand and does the one thing you can do next. */
export function PunchClock() {
  const status = useMyPunch();
  const punch = usePunch();
  const [said, setSaid] = useState<string | null>(null);

  useEffect(() => {
    if (!said) return undefined;
    const timer = setTimeout(() => setSaid(null), 8000);
    return () => clearTimeout(timer);
  }, [said]);

  if (!status.data) return null;
  const { punchedIn, since } = status.data;
  const press = () =>
    punch.mutate(punchedIn ? 'out' : 'in', {
      onSuccess: (view) =>
        setSaid(
          view.punchedIn
            ? null
            : `Punched out at ${clock(view.lastOut ?? '')}. You worked ${duration(view.workedTodayMinutes)} today.`,
        ),
    });

  return (
    <div className="flex items-center gap-2.5 text-sm text-muted">
      <span aria-live="polite" className="flex items-center gap-1.5">
        {punchedIn && since ? (
          <>
            <span className="size-2 rounded-full bg-[#3FBF8A]" aria-hidden="true" />
            <b className="font-semibold text-ink tabular-nums">Punched in at {clock(since)}.</b>
          </>
        ) : (
          (said ?? <span className="hidden sm:inline">Not punched in</span>)
        )}
      </span>
      {punch.error && (
        <span role="alert" className="text-danger-ink">
          {punch.error.detail ?? 'That didn’t go through. Try again.'}
        </span>
      )}
      <button
        type="button"
        onClick={press}
        disabled={punch.isPending}
        className={`${button} ${punchedIn ? 'border border-line bg-transparent text-ink' : 'border-0 bg-accent text-accent-ink'}`}
      >
        {punchedIn ? 'Punch out' : 'Punch in'}
      </button>
    </div>
  );
}
