import { clock } from './format';
import type { Span } from './useAttendance';

const minuteOfDay = (iso: string | number) => {
  const d = new Date(iso);
  return d.getHours() * 60 + d.getMinutes();
};

/**
 * One working day as a strip from morning to evening: each punch is a filled stretch, the brass tick is when work
 * starts, and an open punch runs up to now. Wider than the punches when they start early or end late.
 */
export function DayBar({
  intervals,
  now,
  startsAt = 9 * 60,
  size = 'sm',
}: {
  intervals: Span[];
  now: number;
  startsAt?: number;
  size?: 'sm' | 'lg';
}) {
  const spans = intervals.map((s) => ({
    from: minuteOfDay(s.from),
    to: s.to ? minuteOfDay(s.to) : minuteOfDay(now),
    open: !s.to,
  }));
  const first = Math.min(7 * 60, ...spans.map((s) => s.from));
  const last = Math.max(20 * 60, ...spans.map((s) => s.to));
  const at = (m: number) => `${((m - first) / (last - first)) * 100}%`;
  const label = spans.length
    ? intervals.map((s) => `${clock(s.from)} to ${s.to ? clock(s.to) : 'now'}`).join(', ')
    : 'No punches';
  const height = size === 'lg' ? 'h-3.5' : 'h-2.5';
  return (
    <div className="w-full min-w-0">
      <div
        role="img"
        aria-label={label}
        className={`relative ${height} rounded-full border border-line bg-field`}
      >
        {spans.map((s) => (
          <span
            key={s.from}
            className={`absolute inset-y-[-1px] rounded-full bg-accent ${s.open ? 'punch-open' : ''}`}
            style={{ left: at(s.from), width: `max(6px, calc(${at(s.to)} - ${at(s.from)}))` }}
          />
        ))}
        <span
          aria-hidden="true"
          className="absolute -inset-y-1 w-0.5 rounded-full bg-signal"
          style={{ left: at(startsAt) }}
        />
      </div>
      {size === 'lg' && (
        <div aria-hidden="true" className="relative mt-1.5 h-4 text-[11px] text-muted tabular-nums">
          {[first, startsAt, 13 * 60, last].map((m) => (
            <span key={m} className="absolute -translate-x-1/2" style={{ left: at(m) }}>
              {`${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
