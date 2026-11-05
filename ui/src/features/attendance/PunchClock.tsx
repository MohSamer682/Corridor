import { useEffect, useState } from 'react';
import { clock, duration } from './format';
import { usePunch, useMyPunch, type PunchView } from './useAttendance';
import { stopwatch, useTicker } from './useTicker';

const button =
  'inline-flex h-10 cursor-pointer items-center gap-2 rounded-[10px] px-3.5 text-sm font-semibold transition-[background-color,box-shadow] duration-150 ease-out focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-default disabled:opacity-70';

function ClockIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  );
}

type Note = { tone: 'done' | 'error'; title: string; detail: string };

/**
 * The top bar's punch clock. Out: one clear "Punch in". In: a live stopwatch for this stretch and "Punch out".
 * After punching out, a note under the clock says when and how long today came to.
 */
export function PunchClock() {
  const status = useMyPunch();
  const punch = usePunch();
  const [note, setNote] = useState<Note | null>(null);
  const punchedIn = Boolean(status.data?.punchedIn && status.data.since);
  const now = useTicker(punchedIn);

  useEffect(() => {
    if (note?.tone !== 'done') return undefined;
    const timer = setTimeout(() => setNote(null), 8000);
    return () => clearTimeout(timer);
  }, [note]);

  if (!status.data) return null;
  const view: PunchView = status.data;
  const press = () => {
    setNote(null);
    punch.mutate(punchedIn ? 'out' : 'in', {
      onSuccess: (after) => {
        if (!after.punchedIn)
          setNote({
            tone: 'done',
            title: `Punched out at ${clock(after.lastOut ?? new Date().toISOString())}.`,
            detail:
              after.workedTodayMinutes > 0
                ? `You worked ${duration(after.workedTodayMinutes)} today.`
                : 'That was under a minute, so today is still 0m.',
          });
      },
      onError: (problem) =>
        setNote({
          tone: 'error',
          title: problem.detail ?? 'That didn’t go through.',
          detail: 'Check your connection and try again.',
        }),
    });
  };
  const running = punchedIn && view.since ? stopwatch(now - Date.parse(view.since)) : '';

  return (
    <div className="relative flex items-center gap-2.5">
      {punchedIn && view.since ? (
        <span
          title={`Punched in at ${clock(view.since)}`}
          className="flex h-10 items-center gap-2 rounded-[10px] border border-line bg-field px-3 text-sm"
        >
          <span className="relative flex size-2" aria-hidden="true">
            <span className="absolute inset-0 rounded-full bg-[#3FBF8A] opacity-60 motion-safe:animate-ping" />
            <span className="relative size-2 rounded-full bg-[#3FBF8A]" />
          </span>
          <span className="hidden text-muted sm:inline">In since {clock(view.since)}</span>
          <b
            className="font-semibold text-ink tabular-nums"
            aria-label={`On the clock for ${running}`}
          >
            {running}
          </b>
        </span>
      ) : (
        <span className="hidden items-center gap-1.5 text-sm text-muted md:flex">
          <ClockIcon />
          {view.workedTodayMinutes > 0
            ? `Today ${duration(view.workedTodayMinutes)}`
            : 'Not punched in'}
        </span>
      )}
      <button
        type="button"
        onClick={press}
        disabled={punch.isPending}
        className={`${button} ${
          punchedIn
            ? 'border border-line bg-surface text-ink hover:bg-field'
            : 'border-0 bg-accent text-accent-ink shadow-[0_1px_2px_rgba(20,27,45,.12)] hover:shadow-[0_4px_12px_rgba(35,57,93,.25)]'
        }`}
      >
        {punch.isPending
          ? punchedIn
            ? 'Punching out…'
            : 'Punching in…'
          : punchedIn
            ? 'Punch out'
            : 'Punch in'}
      </button>
      {note && <PunchNote note={note} onRetry={press} onClose={() => setNote(null)} />}
    </div>
  );
}

function PunchNote({
  note,
  onRetry,
  onClose,
}: {
  note: Note;
  onRetry: () => void;
  onClose: () => void;
}) {
  const error = note.tone === 'error';
  return (
    <div
      role={error ? 'alert' : 'status'}
      className={`absolute top-[calc(100%+10px)] right-0 z-20 flex w-[min(320px,calc(100vw-32px))] items-start gap-3 rounded-[12px] border px-4 py-3 text-sm shadow-[0_12px_32px_rgba(20,27,45,.16)] motion-safe:animate-[note-in_220ms_cubic-bezier(.2,.8,.2,1)] ${
        error
          ? 'border-transparent bg-danger-bg text-danger-ink'
          : 'border-line bg-surface text-ink'
      }`}
    >
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        className={`mt-px flex-none ${error ? '' : 'text-accent-text'}`}
      >
        {error ? (
          <path d="M12 8v5M12 16.5h.01M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18Z" />
        ) : (
          <path d="m5 12.5 4.5 4.5L19 7.5" />
        )}
      </svg>
      <span className="flex-1">
        <b className="block font-semibold">{note.title}</b>
        <span className={error ? '' : 'text-muted'}>{note.detail}</span>
        {error && (
          <button
            type="button"
            onClick={onRetry}
            className="mt-2 block cursor-pointer border-0 bg-transparent p-0 font-semibold text-danger-ink underline underline-offset-2"
          >
            Try again
          </button>
        )}
      </span>
      <button
        type="button"
        aria-label="Dismiss"
        onClick={onClose}
        className="-mt-1 -mr-2 grid size-8 flex-none cursor-pointer place-items-center rounded-md border-0 bg-transparent text-current opacity-70 hover:opacity-100"
      >
        <svg
          width="14"
          height="14"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.4"
          strokeLinecap="round"
          aria-hidden="true"
        >
          <path d="M6 6l12 12M18 6 6 18" />
        </svg>
      </button>
    </div>
  );
}
