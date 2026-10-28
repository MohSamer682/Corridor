import { useState } from 'react';
import { apiClient } from '../../shared/api/apiClient';
import { roleLabels, type Role } from '../../shared/roles';
import { Alert } from '../../shared/ui/Alert';
import { initials } from '../auth/initials';
import { useSessionStore } from '../auth/sessionStore';
import { useAdminMutation, usePeople, type TeamView, type UserSummary } from './usePeople';

const secondary =
  'h-11 cursor-pointer rounded-[10px] border border-line bg-transparent px-[18px] font-semibold text-ink';
const primary =
  'h-11 cursor-pointer rounded-[10px] border-0 bg-accent px-[18px] font-semibold text-accent-ink disabled:opacity-75';
const field =
  'h-11 w-full rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none';

export function RequestsList({ teams }: { teams: TeamView[] }) {
  const requests = usePeople({ status: 'PENDING', teamId: '', query: '', page: 0 });
  return (
    <section
      className="rounded-[14px] border border-line bg-surface"
      aria-busy={requests.isPending}
    >
      {requests.isError && (
        <div role="alert" className="p-7 font-semibold text-danger-ink">
          We couldn't load requests.
        </div>
      )}
      {requests.data && requests.data.items.length === 0 && (
        <p className="m-0 px-6 py-12 text-center text-muted">No requests right now.</p>
      )}
      {requests.data?.items.map((request) => (
        <RequestRow key={request.id} request={request} teams={teams} />
      ))}
    </section>
  );
}

function RequestRow({ request, teams }: { request: UserSummary; teams: TeamView[] }) {
  const isAdmin = useSessionStore((state) => state.user?.role) === 'ADMIN';
  const [mode, setMode] = useState<'idle' | 'approving' | 'declining'>('idle');
  const [role, setRole] = useState<Role>('EMPLOYEE');
  const [teamId, setTeamId] = useState('');
  const name = request.displayName ?? '';
  const approve = useAdminMutation(() =>
    apiClient(`/api/v1/users/${request.id}/approval`, {
      method: 'POST',
      body: JSON.stringify({ role, teamId: teamId || null }),
    }),
  );
  const decline = useAdminMutation(() =>
    apiClient(`/api/v1/users/${request.id}/approval`, { method: 'DELETE' }),
  );
  const error = approve.error ?? decline.error;

  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4 last:border-b-0">
      <div className="flex min-w-0 items-center gap-3">
        <span
          aria-hidden="true"
          className="flex size-9 flex-none items-center justify-center rounded-full bg-avatar-bg text-[13px] font-semibold text-avatar-ink"
        >
          {initials(name)}
        </span>
        <div className="min-w-0">
          <span className="block font-semibold">{name}</span>
          <span className="block text-sm [overflow-wrap:anywhere] text-muted">{request.email}</span>
        </div>
      </div>
      <div className="flex gap-2">
        <button
          type="button"
          aria-label={`Decline ${name}`}
          className={secondary}
          onClick={() => setMode('declining')}
        >
          Decline
        </button>
        <button
          type="button"
          aria-label={`Approve ${name}`}
          className={primary}
          onClick={() => setMode('approving')}
        >
          Approve
        </button>
      </div>
      {error && (
        <div className="basis-full">
          <Alert tone="danger">{error.detail ?? 'Something went wrong. Try again.'}</Alert>
        </div>
      )}
      {mode === 'approving' && (
        <div className="flex basis-full flex-wrap items-end gap-3 rounded-[10px] border border-line bg-bg p-3.5">
          <div className="flex-[1_1_180px]">
            <label
              htmlFor={`role-${request.id}`}
              className="mb-1.5 block text-[13px] font-semibold"
            >
              Role
            </label>
            <select
              id={`role-${request.id}`}
              aria-label={`Role for ${name}`}
              className={field}
              value={role}
              onChange={(event) => setRole(event.target.value as Role)}
            >
              {Object.entries(roleLabels)
                .filter(([value]) => isAdmin || value !== 'ADMIN')
                .map(([value, text]) => (
                  <option key={value} value={value}>
                    {text}
                  </option>
                ))}
            </select>
          </div>
          <div className="flex-[1_1_180px]">
            <label
              htmlFor={`team-${request.id}`}
              className="mb-1.5 block text-[13px] font-semibold"
            >
              Team
            </label>
            <select
              id={`team-${request.id}`}
              aria-label={`Team for ${name}`}
              className={field}
              value={teamId}
              onChange={(event) => setTeamId(event.target.value)}
            >
              <option value="">No team</option>
              {teams.map((team) => (
                <option key={team.id} value={team.id}>
                  {team.name}
                </option>
              ))}
            </select>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              className="h-11 cursor-pointer rounded-[10px] border-0 bg-transparent px-2.5 font-semibold text-accent-text"
              onClick={() => setMode('idle')}
            >
              Cancel
            </button>
            <button
              type="button"
              className={primary}
              disabled={approve.isPending}
              onClick={() => approve.mutate(undefined)}
            >
              Approve {name.split(' ')[0]}
            </button>
          </div>
        </div>
      )}
      {mode === 'declining' && (
        <div
          role="group"
          aria-label="Confirm decline"
          className="flex basis-full flex-wrap items-center justify-between gap-3 rounded-[10px] bg-danger-bg p-3.5 text-danger-ink"
        >
          <span>Decline {name}'s request? The account is deleted and they can ask again.</span>
          <span className="flex gap-2">
            <button
              type="button"
              disabled={decline.isPending}
              onClick={() => decline.mutate(undefined)}
              className="h-11 cursor-pointer rounded-[10px] border-0 bg-danger-ink px-[18px] font-semibold text-surface"
            >
              Decline
            </button>
            <button
              type="button"
              onClick={() => setMode('idle')}
              className="h-11 cursor-pointer rounded-[10px] border border-current bg-transparent px-[18px] font-semibold text-inherit"
            >
              Keep
            </button>
          </span>
        </div>
      )}
    </div>
  );
}
