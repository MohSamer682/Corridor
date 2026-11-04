import { useDeferredValue, useState } from 'react';
import { initials } from '../auth/initials';
import { useSessionStore } from '../auth/sessionStore';
import { useTeams } from '../admin-users/usePeople';
import { Alert } from '../../shared/ui/Alert';
import { clock, dayName, duration } from './format';
import { today, useMyWeek, useTeamDay, type WorkDay } from './useAttendance';

const field =
  'h-11 rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none';
const tab =
  'min-h-11 cursor-pointer border-0 border-b-2 bg-transparent px-3 font-medium aria-selected:border-accent aria-selected:text-ink border-transparent text-muted';
const badge =
  'inline-flex h-[26px] items-center gap-1.5 rounded-full px-2.5 text-[13px] font-semibold';

function StatusBadge({ day }: { day: WorkDay }) {
  return (
    <span className="inline-flex flex-wrap gap-1.5">
      {day.inNow && (
        <span className={`${badge} bg-info-bg text-info-ink`}>
          <span className="size-1.5 rounded-full bg-[#3FBF8A]" aria-hidden="true" />
          In now
        </span>
      )}
      {day.status === 'PRESENT' &&
        (day.lateMinutes > 0 ? (
          <span className={`${badge} bg-warn-bg text-warn-ink`}>
            Late {duration(day.lateMinutes)}
          </span>
        ) : (
          !day.inNow && <span className={`${badge} bg-info-bg text-info-ink`}>On time</span>
        ))}
      {day.status === 'ABSENT' && (
        <span className={`${badge} bg-danger-bg text-danger-ink`}>Absent</span>
      )}
      {day.status === 'WEEKEND' && (
        <span className={`${badge} border border-line bg-field text-muted`}>Weekend</span>
      )}
    </span>
  );
}

const intervals = (day: WorkDay) =>
  day.intervals.map((s) => `${clock(s.from)} – ${s.to ? clock(s.to) : 'now'}`);

export function AttendancePage() {
  const role = useSessionStore((state) => state.user?.role);
  const seesOthers = role === 'MANAGER' || role === 'HR' || role === 'ADMIN';
  const [view, setView] = useState<'mine' | 'team'>(seesOthers ? 'team' : 'mine');
  return (
    <div className="mx-auto box-border flex w-full max-w-[1120px] flex-col gap-5 px-4 py-10 sm:px-6">
      <div>
        <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">
          {view === 'team' ? 'Team attendance' : 'My attendance'}
        </h1>
        <p className="m-0 mt-1 text-muted">
          {view === 'team'
            ? 'Who’s in today, and how each day went.'
            : 'Your punches and work days, in your company’s time zone.'}
        </p>
      </div>
      {seesOthers && (
        <div role="tablist" aria-label="Attendance" className="flex gap-1 border-b border-line">
          <button
            type="button"
            role="tab"
            aria-selected={view === 'team'}
            className={tab}
            onClick={() => setView('team')}
          >
            Team attendance
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={view === 'mine'}
            className={tab}
            onClick={() => setView('mine')}
          >
            My attendance
          </button>
        </div>
      )}
      {view === 'team' ? <TeamView canPickTeam={role === 'HR' || role === 'ADMIN'} /> : <MyWeek />}
    </div>
  );
}

function MyWeek() {
  const week = useMyWeek();
  if (week.isError)
    return <Alert tone="danger">We couldn’t load your attendance. Try again.</Alert>;
  const days = week.data ?? [];
  const worked = days.reduce((sum, d) => sum + d.workedMinutes, 0);
  const late = days.reduce((sum, d) => sum + d.lateMinutes, 0);
  const working = days.filter((d) => d.status !== 'WEEKEND' && d.status !== 'NOT_IN_YET');
  const present = working.filter((d) => d.status === 'PRESENT').length;
  return (
    <>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {(
          [
            ['Worked this week', duration(worked)],
            ['Late this week', duration(late)],
            ['Days present', `${present} of ${working.length}`],
          ] as const
        ).map(([label, value]) => (
          <div key={label} className="rounded-[14px] border border-line bg-surface px-[18px] py-4">
            <span className="block text-[13px] text-muted">{label}</span>
            <b className="font-display text-[26px] tracking-[-0.02em] tabular-nums">{value}</b>
          </div>
        ))}
      </div>
      <ul className="m-0 list-none rounded-[14px] border border-line bg-surface p-0">
        {days.map((day) => (
          <li
            key={day.day}
            className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-3 last:border-b-0"
          >
            <div>
              <b className="block">{dayName(day.day)}</b>
              <span className="text-sm text-muted tabular-nums">
                {day.intervals.length
                  ? `${intervals(day).join(', ')} · ${duration(day.workedMinutes)}`
                  : 'No attendance for this day.'}
              </span>
            </div>
            <StatusBadge day={day} />
          </li>
        ))}
      </ul>
    </>
  );
}

// Only HR and admins pick a team; managers always see their own, so they never load the team list.
function TeamPicker({ value, onChange }: { value: string; onChange: (teamId: string) => void }) {
  const teams = useTeams();
  return (
    <>
      <label className="sr-only" htmlFor="att-team">
        Team
      </label>
      <select
        id="att-team"
        className={field}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">All teams</option>
        {teams.data?.items.map((team) => (
          <option key={team.id} value={team.id}>
            {team.name}
          </option>
        ))}
      </select>
    </>
  );
}

function TeamView({ canPickTeam }: { canPickTeam: boolean }) {
  const [day, setDay] = useState(today());
  const [teamId, setTeamId] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const people = useTeamDay(day, teamId, useDeferredValue(query), page);
  if (people.isError)
    return (
      <Alert tone="danger">
        {people.error.detail ?? 'We couldn’t load attendance. Try again.'}
      </Alert>
    );
  const data = people.data;
  return (
    <>
      <div className="flex flex-wrap gap-3">
        <label className="sr-only" htmlFor="att-day">
          Day
        </label>
        <input
          id="att-day"
          type="date"
          className={field}
          value={day}
          max={today()}
          onChange={(e) => (setDay(e.target.value), setPage(0))}
        />
        {canPickTeam && (
          <TeamPicker
            value={teamId}
            onChange={(id) => {
              setTeamId(id);
              setPage(0);
            }}
          />
        )}
        <input
          aria-label="Search by name"
          placeholder="Search by name"
          className={`${field} min-w-0 flex-[1_1_220px]`}
          value={query}
          onChange={(e) => (setQuery(e.target.value), setPage(0))}
        />
      </div>
      <div className="overflow-x-auto rounded-[14px] border border-line bg-surface">
        <table className="w-full border-collapse tabular-nums">
          <thead>
            <tr>
              {['Person', 'First in', 'Last out', 'Worked', 'Status'].map((h) => (
                <th
                  key={h}
                  className="border-b border-line px-5 py-3 text-left text-[13px] font-semibold text-muted"
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {data?.items.map(({ userId, displayName, teamName, day: d }) => {
              const first = d.intervals[0];
              const last = d.intervals.at(-1);
              return (
                <tr key={userId} className="border-b border-line last:border-b-0">
                  <td className="px-5 py-3">
                    <div className="flex items-center gap-3">
                      <span className="flex size-9 flex-none items-center justify-center rounded-full bg-avatar-bg text-[13px] font-semibold text-avatar-ink">
                        {initials(displayName)}
                      </span>
                      <div>
                        <b className="block font-semibold">{displayName}</b>
                        <span className="text-sm text-muted">{teamName ?? 'No team'}</span>
                      </div>
                    </div>
                  </td>
                  <td className="px-5 py-3">{first ? clock(first.from) : '—'}</td>
                  <td className="px-5 py-3">{last?.to ? clock(last.to) : '—'}</td>
                  <td className="px-5 py-3">
                    {d.intervals.length ? duration(d.workedMinutes) : '—'}
                  </td>
                  <td className="px-5 py-3">
                    <StatusBadge day={d} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        {data && (
          <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3 text-sm text-muted">
            <span>
              {data.totalItems} {data.totalItems === 1 ? 'person' : 'people'} · page {data.page + 1}{' '}
              of {Math.max(1, data.totalPages)}
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                className={`${field} cursor-pointer`}
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                Previous
              </button>
              <button
                type="button"
                className={`${field} cursor-pointer`}
                disabled={page + 1 >= data.totalPages}
                onClick={() => setPage(page + 1)}
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>
    </>
  );
}
