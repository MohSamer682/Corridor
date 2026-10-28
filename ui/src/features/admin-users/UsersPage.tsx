import { useDeferredValue, useState } from 'react';
import { roleLabels } from '../../shared/roles';
import { initials } from '../auth/initials';
import { useSessionStore } from '../auth/sessionStore';
import { PersonSheet } from './PersonSheet';
import { RequestsList } from './RequestsList';
import { PAGE_SIZE, usePeople, useTeams, type PeopleFilters, type UserSummary } from './usePeople';

const field =
  'h-11 rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none';
const pagerButton =
  'h-11 cursor-pointer rounded-[10px] border border-line bg-transparent px-[18px] font-semibold text-ink disabled:cursor-default disabled:opacity-50';

type SheetState = { mode: 'add' } | { mode: 'edit'; id: string } | null;

export function UsersPage() {
  const [filters, setFilters] = useState<PeopleFilters>({
    status: 'ACTIVE',
    teamId: '',
    query: '',
    page: 0,
  });
  const deferredFilters = useDeferredValue(filters);
  const people = usePeople(deferredFilters);
  const teams = useTeams();
  const [sheet, setSheet] = useState<SheetState>(null);
  const [tab, setTab] = useState<'everyone' | 'requests'>('everyone');
  const isAdmin = useSessionStore((state) => state.user?.role) === 'ADMIN';
  const requestCount =
    usePeople({ status: 'PENDING', teamId: '', query: '', page: 0 }).data?.totalItems ?? 0;
  const update = (change: Partial<PeopleFilters>) =>
    setFilters((current) => ({ ...current, page: 0, ...change }));

  return (
    <div className="mx-auto box-border flex w-full max-w-[1120px] flex-col gap-5 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">People</h1>
          <p className="m-0 mt-1 text-muted">Everyone who can sign in to your company.</p>
        </div>
        {isAdmin && (
          <button
            type="button"
            onClick={() => setSheet({ mode: 'add' })}
            className="inline-flex h-11 cursor-pointer items-center gap-2 rounded-[10px] border-0 bg-accent px-[18px] font-semibold text-accent-ink"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <path d="M12 5v14M5 12h14" />
            </svg>
            Add person
          </button>
        )}
      </div>
      <div role="tablist" aria-label="People" className="flex gap-1 border-b border-line">
        {(['everyone', 'requests'] as const).map((name) => (
          <button
            key={name}
            type="button"
            role="tab"
            aria-selected={tab === name}
            onClick={() => setTab(name)}
            className={`inline-flex h-11 cursor-pointer items-center gap-2 border-0 border-b-2 bg-transparent px-3.5 font-semibold ${
              tab === name ? 'border-accent text-ink' : 'border-transparent text-muted'
            }`}
          >
            {name === 'everyone' ? 'Everyone' : 'Requests'}
            {name === 'requests' && requestCount > 0 && (
              <span className="grid h-[22px] min-w-[22px] place-items-center rounded-full bg-signal px-1.5 text-xs text-[#141B2D]">
                {requestCount}
              </span>
            )}
          </button>
        ))}
      </div>
      {tab === 'requests' ? (
        <RequestsList teams={teams.data?.items ?? []} />
      ) : (
        <>
          <div className="flex flex-wrap gap-3">
            <input
              type="search"
              aria-label="Search people"
              placeholder="Search by name or email"
              value={filters.query}
              onChange={(event) => update({ query: event.target.value })}
              className={`${field} min-w-0 flex-[1_1_260px]`}
            />
            <label className="sr-only" htmlFor="status-filter">
              Status
            </label>
            <select
              id="status-filter"
              value={filters.status}
              onChange={(event) =>
                update({ status: event.target.value as PeopleFilters['status'] })
              }
              className={`${field} flex-1 sm:flex-none`}
            >
              <option value="">All statuses</option>
              <option value="ACTIVE">Active</option>
              <option value="SUSPENDED">Suspended</option>
            </select>
            <label className="sr-only" htmlFor="team-filter">
              Team
            </label>
            <select
              id="team-filter"
              value={filters.teamId}
              onChange={(event) => update({ teamId: event.target.value })}
              className={`${field} flex-1 sm:flex-none`}
            >
              <option value="">All teams</option>
              {teams.data?.items.map((team) => (
                <option key={team.id} value={team.id}>
                  {team.name}
                </option>
              ))}
            </select>
          </div>
          <section
            className="rounded-[14px] border border-line bg-surface"
            aria-busy={people.isPending}
          >
            {people.isError && (
              <div role="alert" className="flex flex-col items-start gap-3 p-7">
                <span className="font-semibold text-danger-ink">We couldn't load people.</span>
                <button type="button" className={pagerButton} onClick={() => people.refetch()}>
                  Try again
                </button>
              </div>
            )}
            {people.data && people.data.items.length === 0 && (
              <div className="flex flex-col items-center gap-2.5 px-6 py-14 text-center">
                <p className="m-0 font-display text-xl font-bold">No people match these filters.</p>
                <p className="m-0 text-muted">Try another name, or show everyone.</p>
                <button
                  type="button"
                  className={pagerButton}
                  onClick={() => setFilters({ status: '', teamId: '', query: '', page: 0 })}
                >
                  Clear filters
                </button>
              </div>
            )}
            {people.data && people.data.items.length > 0 && (
              <PeopleTable
                people={people.data.items}
                onEdit={(id) => setSheet({ mode: 'edit', id })}
              />
            )}
            {people.data && people.data.totalItems > 0 && (
              <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3 text-sm text-muted">
                <span>
                  {filters.page * PAGE_SIZE + 1}–
                  {filters.page * PAGE_SIZE + people.data.items.length} of {people.data.totalItems}{' '}
                  people
                </span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    className={pagerButton}
                    disabled={filters.page === 0}
                    onClick={() =>
                      setFilters((current) => ({ ...current, page: current.page - 1 }))
                    }
                  >
                    Previous
                  </button>
                  <button
                    type="button"
                    className={pagerButton}
                    disabled={filters.page + 1 >= people.data.totalPages}
                    onClick={() =>
                      setFilters((current) => ({ ...current, page: current.page + 1 }))
                    }
                  >
                    Next
                  </button>
                </div>
              </div>
            )}
          </section>
        </>
      )}
      <PersonSheet state={sheet} teams={teams.data?.items ?? []} onClose={() => setSheet(null)} />
    </div>
  );
}

function PeopleTable({ people, onEdit }: { people: UserSummary[]; onEdit: (id: string) => void }) {
  return (
    <table className="w-full border-collapse tabular-nums">
      <thead>
        <tr className="text-left text-[13px] font-semibold text-muted">
          <th className="border-b border-line px-5 py-3">Name</th>
          <th className="hidden border-b border-line px-5 py-3 md:table-cell">Team</th>
          <th className="hidden border-b border-line px-5 py-3 md:table-cell">Role</th>
          <th className="hidden border-b border-line px-5 py-3 md:table-cell">Status</th>
          <th className="border-b border-line px-5 py-3">
            <span className="sr-only">Actions</span>
          </th>
        </tr>
      </thead>
      <tbody>
        {people.map((person) => (
          <PersonRow key={person.id} person={person} onEdit={onEdit} />
        ))}
      </tbody>
    </table>
  );
}

function PersonRow({ person, onEdit }: { person: UserSummary; onEdit: (id: string) => void }) {
  const role = person.role ? roleLabels[person.role] : '';
  const name = person.displayName ?? '';
  return (
    <tr className="border-b border-line last:border-b-0 hover:bg-field">
      <td className="px-5 py-3">
        <div className="flex min-w-0 items-center gap-3">
          <span
            className="flex size-9 flex-none items-center justify-center rounded-full bg-avatar-bg text-[13px] font-semibold text-avatar-ink"
            aria-hidden="true"
          >
            {initials(name)}
          </span>
          <div className="min-w-0">
            <span className="block font-semibold">{name}</span>
            <span className="block text-sm [overflow-wrap:anywhere] text-muted">
              {person.email}
            </span>
            <span className="mt-1 flex flex-wrap items-center gap-2 text-sm text-muted md:hidden">
              {role}
              {person.teamName && ` · ${person.teamName}`}
              {person.status === 'SUSPENDED' && <StatusBadge status="SUSPENDED" />}
            </span>
          </div>
        </div>
      </td>
      <td className="hidden px-5 py-3 md:table-cell">
        {person.teamName ? person.teamName : <span className="text-muted">No team</span>}
      </td>
      <td className="hidden px-5 py-3 md:table-cell">{role}</td>
      <td className="hidden px-5 py-3 md:table-cell">
        <StatusBadge status={person.status === 'SUSPENDED' ? 'SUSPENDED' : 'ACTIVE'} />
      </td>
      <td className="px-5 py-3 text-right">
        <button
          type="button"
          aria-label={`Edit ${name}`}
          onClick={() => person.id && onEdit(person.id)}
          className="h-11 cursor-pointer rounded-[10px] border-0 bg-transparent px-2.5 font-semibold text-accent-text"
        >
          Edit
        </button>
      </td>
    </tr>
  );
}

function StatusBadge({ status }: { status: 'ACTIVE' | 'SUSPENDED' }) {
  return status === 'ACTIVE' ? (
    <span className="inline-flex h-[26px] items-center gap-1.5 rounded-full bg-info-bg px-2.5 text-[13px] font-semibold text-info-ink">
      <span className="size-1.5 rounded-full bg-signal" aria-hidden="true" />
      Active
    </span>
  ) : (
    <span className="inline-flex h-[26px] items-center rounded-full bg-danger-bg px-2.5 text-[13px] font-semibold text-danger-ink">
      Suspended
    </span>
  );
}
