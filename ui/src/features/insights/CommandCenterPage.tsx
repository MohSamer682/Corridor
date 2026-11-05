import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { apiClient } from '../../shared/api/apiClient';
import { useCampus } from '../office/useOffice';

type Kpis = {
  people: number;
  online: number;
  inMeetings: number;
  inFocus: number;
  punchedIn: number;
  late: number;
  roomsInUse: number;
  rooms: number;
};
type RoomUse = {
  roomId: string;
  name: string;
  place: string;
  meeting: string | null;
  people: number;
  capacity: number;
};
type TeamRow = {
  team: string;
  people: number;
  online: number;
  punchedIn: number;
  late: number;
  inMeetings: number;
};
type DayCount = { day: string; present: number; weekend: boolean };
type Snapshot = {
  at: string;
  scope: string;
  kpis: Kpis;
  floors: { floorId: string; online: number }[];
  rooms: RoomUse[];
  teams: TeamRow[];
  attention: string[];
  days: DayCount[];
};

const card = 'rounded-[14px] border border-line bg-surface';
const sectionTitle = 'm-0 font-display text-lg font-bold tracking-[-0.01em]';
const people = (n: number) => `${n} ${n === 1 ? 'person' : 'people'}`;
const dayLabel = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  timeZone: 'UTC',
});
const time = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });

/** Leaders' view of the company right now: who is in, where, and what needs a look. Refreshes every minute. */
export function CommandCenterPage() {
  const snapshot = useQuery({
    queryKey: ['command-center'],
    queryFn: () => apiClient<Snapshot>('/api/v1/command-center'),
    refetchInterval: 60_000,
  });
  const data = snapshot.data;
  return (
    <div className="mx-auto box-border flex w-full max-w-[1120px] flex-col gap-5 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">
            Command Center
          </h1>
          {data && (
            <p className="m-0 mt-1 text-muted">
              {data.scope} · {people(data.kpis.people)}
            </p>
          )}
        </div>
        {data && <p className="m-0 text-sm text-muted">Updated {time.format(new Date(data.at))}</p>}
      </div>
      {snapshot.isPending && <p className="m-0 text-muted">Loading…</p>}
      {snapshot.isError && (
        <p role="alert" className="m-0 font-semibold text-danger-ink">
          We couldn't load the Command Center. It tries again every minute.
        </p>
      )}
      {data && <Dashboard data={data} />}
    </div>
  );
}

function Dashboard({ data }: { data: Snapshot }) {
  return (
    <>
      <KpiRow kpis={data.kpis} />
      <div className="grid gap-5 lg:grid-cols-2">
        <Attention lines={data.attention} />
        <Floors counts={data.floors} />
      </div>
      <div className="grid gap-5 lg:grid-cols-2">
        <Rooms rooms={data.rooms} />
        <History days={data.days} />
      </div>
      <Teams rows={data.teams} />
    </>
  );
}

function KpiRow({ kpis }: { kpis: Kpis }) {
  const tiles: [string, string][] = [
    ['Online', String(kpis.online)],
    ['Punched in', String(kpis.punchedIn)],
    ['In meetings', String(kpis.inMeetings)],
    ['In focus', String(kpis.inFocus)],
    ['Late today', String(kpis.late)],
    ['Rooms in use', `${kpis.roomsInUse} of ${kpis.rooms}`],
  ];
  return (
    <ul
      aria-label="Right now"
      className="m-0 grid list-none grid-cols-2 gap-3 p-0 sm:grid-cols-3 lg:grid-cols-6"
    >
      {tiles.map(([label, value]) => (
        <li key={label} className={`${card} flex flex-col gap-1 px-4 py-3.5`}>
          <span className="text-[13px] font-semibold text-muted">{label}</span>
          <span className="font-display text-[28px] leading-none font-bold tracking-[-0.02em] tabular-nums">
            {value}
          </span>
        </li>
      ))}
    </ul>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  const id = `cc-${title.toLowerCase().replace(/\W+/g, '-')}`;
  return (
    <section aria-labelledby={id} className={`${card} flex min-w-0 flex-col gap-4 p-5 sm:p-6`}>
      <h2 id={id} className={sectionTitle}>
        {title}
      </h2>
      {children}
    </section>
  );
}

function Attention({ lines }: { lines: string[] }) {
  return (
    <Section title="Needs attention">
      {lines.length === 0 ? (
        <p className="m-0 text-muted">Nothing right now.</p>
      ) : (
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {lines.map((line) => (
            <li
              key={line}
              className="rounded-[10px] bg-warn-bg px-3.5 py-2.5 text-sm text-warn-ink"
            >
              {line}
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}

/** A filled share of {@code max}; the number beside it carries the value, the bar only the proportion. */
function Meter({ value, max, tone }: { value: number; max: number; tone: string }) {
  const width = max > 0 ? Math.max(value > 0 ? 4 : 0, Math.round((value / max) * 100)) : 0;
  return (
    <span className="block h-2 w-full overflow-hidden rounded-full bg-field" aria-hidden="true">
      <span
        className={`block h-full rounded-full ${tone}`}
        style={{ width: `${Math.min(width, 100)}%` }}
      />
    </span>
  );
}

function Floors({ counts }: { counts: Snapshot['floors'] }) {
  const campus = useCampus();
  const byFloor = new Map(counts.map((c) => [c.floorId, c.online]));
  const floors =
    campus.data?.buildings.flatMap((b) =>
      [...b.floors]
        .sort((x, y) => x.level - y.level)
        .map((f) => ({ id: f.id, label: `${f.code} · ${f.name}`, building: b.name })),
    ) ?? [];
  const max = Math.max(1, ...counts.map((c) => c.online));
  return (
    <Section title="Floors">
      {floors.length === 0 && <p className="m-0 text-muted">Loading floors…</p>}
      <ul className="m-0 flex list-none flex-col gap-3 p-0">
        {floors.map((floor) => {
          const online = byFloor.get(floor.id) ?? 0;
          return (
            <li key={floor.id} className="grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1.5">
              <span className="min-w-0 truncate text-sm">
                <span>{floor.label}</span>
                <span className="text-muted"> · {floor.building}</span>
              </span>
              <span className="text-sm font-semibold tabular-nums">{people(online)}</span>
              <span className="col-span-2">
                <Meter value={online} max={max} tone="bg-signal" />
              </span>
            </li>
          );
        })}
      </ul>
    </Section>
  );
}

function Rooms({ rooms }: { rooms: RoomUse[] }) {
  return (
    <Section title="Rooms">
      {rooms.length === 0 && <p className="m-0 text-muted">No meeting rooms yet.</p>}
      <ul className="m-0 flex list-none flex-col gap-3 p-0">
        {rooms.map((room) => (
          <li
            key={room.roomId}
            className="grid grid-cols-[1fr_auto] items-center gap-x-3 gap-y-1.5"
          >
            <span className="min-w-0 truncate text-sm font-semibold">{room.name}</span>
            <span className="text-[13px] text-muted">{room.place}</span>
            <span className="col-span-2 text-sm text-muted">
              {room.meeting ? `${room.meeting} · ${room.people} of ${room.capacity} seats` : 'Free'}
            </span>
            {room.meeting && (
              <span className="col-span-2">
                <Meter value={room.people} max={room.capacity} tone="bg-accent" />
              </span>
            )}
          </li>
        ))}
      </ul>
    </Section>
  );
}

const W = 360;
const H = 160;
const GAP = 4;

function History({ days }: { days: DayCount[] }) {
  const max = Math.max(1, ...days.map((d) => d.present));
  const bar = (W - GAP * (days.length - 1)) / days.length;
  const last = days.length - 1;
  return (
    <Section title="Punched in, last 14 days">
      <svg
        viewBox={`0 0 ${W} ${H + 22}`}
        className="h-auto w-full"
        role="img"
        aria-label={`Most in one day: ${max}. Today: ${days[last]?.present ?? 0}.`}
      >
        <line x1="0" x2={W} y1={H} y2={H} className="stroke-line" strokeWidth="1" />
        {days.map((d, i) => {
          const h = d.present === 0 ? 0 : Math.max(4, (d.present / max) * (H - 18));
          const x = i * (bar + GAP);
          const label = dayLabel.format(new Date(d.day));
          return (
            <g key={d.day}>
              <title>{`${label}: ${people(d.present)}${d.weekend ? ' (weekend)' : ''}`}</title>
              <rect x={x} y={0} width={bar} height={H} fill="transparent" />
              {h > 0 && (
                <rect
                  x={x}
                  y={H - h}
                  width={bar}
                  height={h}
                  rx="4"
                  className={i === last ? 'fill-signal' : d.weekend ? 'fill-line' : 'fill-accent'}
                />
              )}
              {i % 2 === last % 2 && (
                <text
                  x={i === last ? x + bar : x + bar / 2}
                  y={H + 16}
                  textAnchor={i === last ? 'end' : 'middle'}
                  className="fill-muted text-[12px]"
                >
                  {i === last ? 'Today' : label.split(' ')[1]}
                </text>
              )}
            </g>
          );
        })}
      </svg>
      <details className="text-sm">
        <summary className="cursor-pointer font-semibold text-accent-text">Show as a table</summary>
        <table className="mt-2 w-full border-collapse tabular-nums">
          <caption className="sr-only">Punched in, last 14 days</caption>
          <thead>
            <tr className="text-left text-[13px] text-muted">
              <th className="border-b border-line py-1.5">Day</th>
              <th className="border-b border-line py-1.5 text-right">Punched in</th>
            </tr>
          </thead>
          <tbody>
            {days.map((d) => (
              <tr key={d.day}>
                <td className="border-b border-line py-1.5">
                  {dayLabel.format(new Date(d.day))}
                  {d.weekend && <span className="text-muted"> · weekend</span>}
                </td>
                <td className="border-b border-line py-1.5 text-right">{d.present}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </Section>
  );
}

function Teams({ rows }: { rows: TeamRow[] }) {
  const head = ['People', 'Online', 'Punched in', 'Late', 'In meetings'];
  return (
    <section className={`${card} overflow-x-auto`}>
      <table className="w-full min-w-[560px] border-collapse tabular-nums">
        <caption className="px-6 pt-5 pb-2 text-left">
          <span className={sectionTitle}>Teams</span>
        </caption>
        <thead>
          <tr className="text-left text-[13px] font-semibold text-muted">
            <th className="border-b border-line px-6 py-3">Team</th>
            {head.map((h) => (
              <th key={h} className="border-b border-line px-4 py-3 text-right">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.team}>
              <th scope="row" className="border-b border-line px-6 py-3 text-left font-semibold">
                {row.team}
              </th>
              {[row.people, row.online, row.punchedIn, row.late, row.inMeetings].map((n, i) => (
                <td key={head[i]} className="border-b border-line px-4 py-3 text-right">
                  {n}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
