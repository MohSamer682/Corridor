import { useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { apiClient } from '../../shared/api/apiClient';
import { roleLabels, type Role } from '../../shared/roles';
import { Alert } from '../../shared/ui/Alert';
import { initials } from '../auth/initials';
import type { UserSummary } from '../admin-users/usePeople';
import { describeEvent } from './describeEvent';
import {
  NO_FILTERS,
  useAuditEvents,
  useChainVerification,
  type AuditEvent,
  type AuditFilters,
} from './useAuditLog';

const field =
  'h-11 rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none';
const secondaryButton =
  'h-11 cursor-pointer rounded-[10px] border border-line bg-transparent px-[18px] font-semibold text-ink';
const timeFormat = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
const dayFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' });
const weekdayFormat = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

export function AuditLogPage() {
  const [filters, setFilters] = useState<AuditFilters>(NO_FILTERS);
  const events = useAuditEvents(useDeferredValue(filters));
  const filtered = Object.values(filters).some(Boolean);
  const items = events.data?.pages.flatMap((page) => page.items) ?? [];

  return (
    <div className="mx-auto box-border flex w-full max-w-[1120px] flex-col gap-5 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">Audit log</h1>
          <p className="m-0 mt-1 text-muted">
            Every change to people and teams, and every failed sign-in. Kept for 3 years.
          </p>
        </div>
        <Seal />
      </div>
      <AlteredWarning />
      <Filters filters={filters} onChange={setFilters} />
      <section
        className="rounded-[14px] border border-line bg-surface"
        aria-busy={events.isPending}
      >
        {events.isPending && <Skeleton />}
        {events.isError && (
          <div className="flex flex-col items-center gap-3 px-6 py-14 text-center">
            <Alert tone="danger">
              We couldn't load the audit log. Check your connection and try again.
            </Alert>
            <button type="button" className={secondaryButton} onClick={() => events.refetch()}>
              Try again
            </button>
          </div>
        )}
        {events.isSuccess && items.length === 0 && (
          <div className="flex flex-col items-center gap-2.5 px-6 py-14 text-center">
            <p className="m-0 font-display text-xl font-bold">No events match these filters.</p>
            <p className="m-0 text-muted">Try a wider date range or another person.</p>
            {filtered && (
              <button
                type="button"
                className={secondaryButton}
                onClick={() => setFilters(NO_FILTERS)}
              >
                Clear filters
              </button>
            )}
          </div>
        )}
        {items.length > 0 && (
          <>
            <EventTable days={byDay(items)} />
            <EventList days={byDay(items)} />
            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3 text-sm text-muted">
              {events.hasNextPage ? (
                <>
                  <span>Showing the newest {items.length} events</span>
                  <button
                    type="button"
                    className={secondaryButton}
                    disabled={events.isFetchingNextPage}
                    onClick={() => events.fetchNextPage()}
                  >
                    {events.isFetchingNextPage ? 'Loading…' : 'Load older events'}
                  </button>
                </>
              ) : (
                <span>
                  {filtered ? "That's everything for these filters." : `${items.length} events`}
                </span>
              )}
            </div>
          </>
        )}
      </section>
    </div>
  );
}

function Seal() {
  const verification = useChainVerification();
  if (!verification.data) return null;
  const { intact, checked } = verification.data;
  return (
    <span
      role="status"
      className={`inline-flex h-9 items-center gap-2 rounded-full px-3.5 text-sm font-semibold ${
        intact ? 'bg-info-bg text-info-ink' : 'bg-danger-bg text-danger-ink'
      }`}
    >
      <svg
        width="16"
        height="16"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2.2"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
      >
        <path d="M12 3 5 6v5c0 4.5 3 8.3 7 10 4-1.7 7-5.5 7-10V6l-7-3Z" />
        <path d={intact ? 'm9 12 2 2 4-4' : 'M12 8v4M12 15h.01'} />
      </svg>
      {intact ? (
        <span>
          Log intact
          <span className="hidden sm:inline">
            {' '}
            · {checked.toLocaleString('en-GB')} entries checked
          </span>
        </span>
      ) : (
        'Log altered'
      )}
    </span>
  );
}

function AlteredWarning() {
  const verification = useChainVerification();
  const alteredAt = verification.data?.firstAlteredAt;
  if (verification.data?.intact !== false || !alteredAt) return null;
  const at = new Date(alteredAt);
  return (
    <Alert tone="danger">
      The entry from {dayFormat.format(at)} at {timeFormat.format(at)} was changed outside Corridor,
      so it and every entry after it can't be trusted. Back up the database before anything else
      changes, then check who had access to it.
    </Alert>
  );
}

function Filters({
  filters,
  onChange,
}: {
  filters: AuditFilters;
  onChange: (next: AuditFilters) => void;
}) {
  // shortcut: lists the first 100 people; switch to a search box when companies outgrow that.
  const people = useQuery({
    queryKey: ['users', 'audit-filter'],
    queryFn: () => apiClient<{ items: UserSummary[] }>('/api/v1/users?size=100'),
  });
  const update = (change: Partial<AuditFilters>) => onChange({ ...filters, ...change });
  return (
    <div className="flex flex-wrap items-center gap-3">
      <label className="sr-only" htmlFor="audit-action">
        Action
      </label>
      <select
        id="audit-action"
        value={filters.action}
        onChange={(event) => update({ action: event.target.value as AuditFilters['action'] })}
        className={`${field} flex-1 sm:flex-none`}
      >
        <option value="">All actions</option>
        <option value="people">People changes</option>
        <option value="teams">Team changes</option>
        <option value="sign-in">Failed sign-ins</option>
      </select>
      <label className="sr-only" htmlFor="audit-actor">
        Person
      </label>
      <select
        id="audit-actor"
        value={filters.actorId}
        onChange={(event) => update({ actorId: event.target.value })}
        className={`${field} flex-1 sm:min-w-[220px] sm:flex-none`}
      >
        <option value="">Anyone</option>
        {people.data?.items.map((person) => (
          <option key={person.id} value={person.id}>
            {person.displayName}
          </option>
        ))}
      </select>
      <div className="grid w-full grid-cols-[auto_1fr] items-center gap-2 sm:flex sm:w-auto">
        <label className="text-sm text-muted" htmlFor="audit-from">
          From
        </label>
        <input
          id="audit-from"
          type="date"
          value={filters.from}
          max={filters.to || undefined}
          onChange={(event) => update({ from: event.target.value })}
          className={`${field} min-w-0`}
        />
        <label className="text-sm text-muted" htmlFor="audit-to">
          To
        </label>
        <input
          id="audit-to"
          type="date"
          value={filters.to}
          min={filters.from || undefined}
          onChange={(event) => update({ to: event.target.value })}
          className={`${field} min-w-0`}
        />
      </div>
      {Object.values(filters).some(Boolean) && (
        <button
          type="button"
          onClick={() => onChange(NO_FILTERS)}
          className="h-11 cursor-pointer rounded-[10px] border-0 bg-transparent px-2.5 font-semibold text-accent-text"
        >
          Clear filters
        </button>
      )}
    </div>
  );
}

type Day = { label: string; events: AuditEvent[] };

function byDay(events: AuditEvent[]): Day[] {
  const days: Day[] = [];
  for (const event of events) {
    const label = dayLabel(new Date(event.createdAt));
    if (days.at(-1)?.label !== label) days.push({ label, events: [] });
    days.at(-1)?.events.push(event);
  }
  return days;
}

function dayLabel(at: Date) {
  const today = new Date();
  const yesterday = new Date();
  yesterday.setDate(today.getDate() - 1);
  if (at.toDateString() === today.toDateString()) return `Today, ${dayFormat.format(at)}`;
  if (at.toDateString() === yesterday.toDateString()) return `Yesterday, ${dayFormat.format(at)}`;
  return weekdayFormat.format(at);
}

function actorOf(event: AuditEvent) {
  const { actorName, actorRole, ip } = event.details;
  return actorName
    ? { name: actorName, note: roleLabels[actorRole as Role] ?? actorRole ?? '' }
    : { name: 'Unknown', note: ip ?? '' };
}

function Avatar({ name }: { name: string }) {
  const unknown = name === 'Unknown';
  return (
    <span
      aria-hidden="true"
      className={`flex size-9 flex-none items-center justify-center rounded-full text-[13px] font-semibold ${
        unknown
          ? 'border border-dashed border-field-line text-muted'
          : 'bg-avatar-bg text-avatar-ink'
      }`}
    >
      {unknown ? '?' : initials(name)}
    </span>
  );
}

function KindTag({ kind }: { kind: string }) {
  return (
    <span
      className={`inline-flex h-[26px] items-center rounded-md px-2.5 text-[13px] font-semibold whitespace-nowrap ${
        kind === 'Sign-in'
          ? 'bg-danger-bg text-danger-ink'
          : 'border border-line bg-field text-muted'
      }`}
    >
      {kind}
    </span>
  );
}

function Sentence({ event }: { event: AuditEvent }) {
  const description = describeEvent(event);
  return (
    <>
      {description.lead} <b className="font-semibold">{description.subject}</b>
      {description.tail}
      {description.detail && <span className="block text-sm text-muted">{description.detail}</span>}
    </>
  );
}

function EventTable({ days }: { days: Day[] }) {
  return (
    <table className="hidden w-full border-collapse tabular-nums sm:table">
      <thead>
        <tr className="text-left text-[13px] font-semibold text-muted">
          <th className="border-b border-line px-5 py-3">When</th>
          <th className="border-b border-line px-5 py-3">Who</th>
          <th className="border-b border-line px-5 py-3">What happened</th>
          <th className="border-b border-line px-5 py-3">Type</th>
        </tr>
      </thead>
      {days.map((day) => (
        <tbody key={day.label}>
          <tr>
            <th
              colSpan={4}
              scope="colgroup"
              className="border-b border-line bg-bg px-5 py-2 text-left text-[13px] font-semibold text-muted"
            >
              {day.label}
            </th>
          </tr>
          {day.events.map((event) => {
            const actor = actorOf(event);
            return (
              <tr key={event.id} className="border-b border-line hover:bg-field">
                <td className="w-px px-5 py-3 whitespace-nowrap text-muted">
                  {timeFormat.format(new Date(event.createdAt))}
                </td>
                <td className="px-5 py-3">
                  <div className="flex items-center gap-3">
                    <Avatar name={actor.name} />
                    <div>
                      <span className="block font-semibold">{actor.name}</span>
                      <span className="block text-sm text-muted">{actor.note}</span>
                    </div>
                  </div>
                </td>
                <td className="px-5 py-3 [overflow-wrap:anywhere]">
                  <Sentence event={event} />
                </td>
                <td className="px-5 py-3">
                  <KindTag kind={describeEvent(event).kind} />
                </td>
              </tr>
            );
          })}
        </tbody>
      ))}
    </table>
  );
}

function EventList({ days }: { days: Day[] }) {
  return (
    <ol className="m-0 list-none p-0 sm:hidden">
      {days.map((day) => (
        <li key={day.label}>
          <div className="border-b border-line bg-bg px-4 py-2 text-[13px] font-semibold text-muted">
            {day.label}
          </div>
          <ol className="m-0 list-none p-0">
            {day.events.map((event) => {
              const actor = actorOf(event);
              return (
                <li key={event.id} className="flex gap-3 border-b border-line px-4 py-3.5">
                  <Avatar name={actor.name} />
                  <div className="min-w-0 [overflow-wrap:anywhere]">
                    <Sentence event={event} />
                    <div className="mt-1.5 flex flex-wrap items-center gap-2 text-[13px] text-muted">
                      {timeFormat.format(new Date(event.createdAt))} · {actor.name}
                      <KindTag kind={describeEvent(event).kind} />
                    </div>
                  </div>
                </li>
              );
            })}
          </ol>
        </li>
      ))}
    </ol>
  );
}

function Skeleton() {
  return (
    <div className="flex flex-col gap-4 p-5" aria-hidden="true">
      {[320, 260, 300].map((width) => (
        <div key={width} className="flex items-center gap-6">
          <div className="h-3.5 w-12 rounded-md bg-line opacity-60" />
          <div className="h-3.5 w-40 rounded-md bg-line opacity-60" />
          <div className="h-3.5 rounded-md bg-line opacity-60" style={{ width }} />
        </div>
      ))}
    </div>
  );
}
