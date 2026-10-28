import { useState } from 'react';
import { apiClient } from '../../shared/api/apiClient';
import { Alert } from '../../shared/ui/Alert';
import { ifMatch, useAdminMutation, useTeams, type TeamView } from './usePeople';

const swatches = [
  { name: 'Navy', color: '#23395D' },
  { name: 'Teal', color: '#2F7D6D' },
  { name: 'Brass', color: '#B08A4E' },
  { name: 'Brick', color: '#A04A3B' },
  { name: 'Plum', color: '#6A4C8C' },
  { name: 'Slate', color: '#4F5D75' },
];
const field =
  'h-11 w-full rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none';
const linkButton =
  'h-11 cursor-pointer rounded-[10px] border-0 bg-transparent px-2.5 font-semibold text-accent-text';

export function TeamsPage() {
  const teams = useTeams();
  return (
    <div className="mx-auto box-border flex w-full max-w-[1120px] flex-col gap-5 px-4 py-10 sm:px-6">
      <div>
        <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">Teams</h1>
        <p className="m-0 mt-1 text-muted">
          Group people so they can find each other in the office.
        </p>
      </div>
      <AddTeamForm />
      <section className="rounded-[14px] border border-line bg-surface" aria-busy={teams.isPending}>
        {teams.isError && (
          <div role="alert" className="p-7 font-semibold text-danger-ink">
            We couldn't load teams.
          </div>
        )}
        {teams.data && teams.data.items.length === 0 && (
          <p className="m-0 px-6 py-12 text-center text-muted">
            No teams yet. Add the first one above.
          </p>
        )}
        {teams.data && teams.data.items.length > 0 && (
          <table className="w-full border-collapse tabular-nums">
            <thead>
              <tr className="text-left text-[13px] font-semibold text-muted">
                <th className="border-b border-line px-5 py-3">Team</th>
                <th className="border-b border-line px-5 py-3">People</th>
                <th className="border-b border-line px-5 py-3">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {teams.data.items.map((team) => (
                <TeamRow key={team.id} team={team} />
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}

function AddTeamForm() {
  const [name, setName] = useState('');
  const [color, setColor] = useState('#23395D');
  const add = useAdminMutation(() =>
    apiClient('/api/v1/teams', {
      method: 'POST',
      body: JSON.stringify({ name: name.trim(), color }),
    }),
  );
  return (
    <form
      className="flex flex-wrap items-end gap-3.5 rounded-[14px] border border-line bg-surface px-[22px] py-5"
      onSubmit={(event) => {
        event.preventDefault();
        add.mutate(undefined, { onSuccess: () => setName('') });
      }}
    >
      <div className="min-w-0 flex-[1_1_260px]">
        <label htmlFor="team-name" className="mb-1.5 block text-[13px] font-semibold">
          Team name
        </label>
        <input
          id="team-name"
          className={field}
          placeholder="Platform"
          required
          minLength={2}
          maxLength={60}
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>
      <fieldset className="m-0 border-0 p-0">
        <legend className="mb-1.5 block p-0 text-[13px] font-semibold">Color</legend>
        <div className="flex flex-wrap gap-2">
          {swatches.map((swatch) => (
            <label key={swatch.color} className="relative cursor-pointer">
              <input
                type="radio"
                name="team-color"
                aria-label={swatch.name}
                value={swatch.color}
                checked={color === swatch.color}
                onChange={() => setColor(swatch.color)}
                className="peer sr-only"
              />
              <span
                className="block size-8 rounded-lg border-2 border-transparent peer-checked:border-ink peer-checked:shadow-[inset_0_0_0_2px_var(--surface)] peer-focus-visible:ring-2 peer-focus-visible:ring-signal"
                style={{ background: swatch.color }}
              />
            </label>
          ))}
        </div>
      </fieldset>
      <button
        type="submit"
        disabled={add.isPending}
        className="h-11 cursor-pointer rounded-[10px] border-0 bg-accent px-[18px] font-semibold text-accent-ink disabled:opacity-75"
      >
        Add team
      </button>
      {add.error && (
        <div className="basis-full">
          <Alert tone="danger">{add.error.detail ?? 'Something went wrong. Try again.'}</Alert>
        </div>
      )}
    </form>
  );
}

function TeamRow({ team }: { team: TeamView }) {
  const [renaming, setRenaming] = useState(false);
  const [name, setName] = useState(team.name ?? '');
  const rename = useAdminMutation(() =>
    apiClient(`/api/v1/teams/${team.id}`, {
      method: 'PATCH',
      headers: ifMatch(team.version),
      body: JSON.stringify({ name: name.trim(), color: team.color }),
    }),
  );
  const remove = useAdminMutation(() =>
    apiClient(`/api/v1/teams/${team.id}`, { method: 'DELETE' }),
  );
  const error = rename.error ?? remove.error;

  return (
    <>
      <tr className={error ? '' : 'border-b border-line last:border-b-0'}>
        <td className="px-5 py-3">
          {renaming ? (
            <form
              className="flex gap-2"
              onSubmit={(event) => {
                event.preventDefault();
                rename.mutate(undefined, { onSuccess: () => setRenaming(false) });
              }}
            >
              <label className="sr-only" htmlFor={`rename-${team.id}`}>
                New name for {team.name}
              </label>
              <input
                id={`rename-${team.id}`}
                className={field}
                value={name}
                onChange={(event) => setName(event.target.value)}
              />
              <button type="submit" className={linkButton}>
                Save
              </button>
            </form>
          ) : (
            <span className="inline-flex items-center gap-2 font-semibold">
              <span
                className="size-3.5 flex-none rounded"
                style={{ background: team.color }}
                aria-hidden="true"
              />
              {team.name}
            </span>
          )}
        </td>
        <td className="px-5 py-3">
          {team.memberCount ? team.memberCount : <span className="text-muted">No one yet</span>}
        </td>
        <td className="px-5 py-3 text-right whitespace-nowrap">
          <button
            type="button"
            aria-label={`Rename ${team.name}`}
            className={linkButton}
            onClick={() => setRenaming((on) => !on)}
          >
            {renaming ? 'Cancel' : 'Rename'}
          </button>
          <button
            type="button"
            aria-label={`Delete ${team.name}`}
            className={linkButton}
            disabled={remove.isPending}
            onClick={() => remove.mutate(undefined)}
          >
            Delete
          </button>
        </td>
      </tr>
      {error && (
        <tr className="border-b border-line last:border-b-0">
          <td colSpan={3} className="px-5 pb-3">
            <Alert tone="danger">{error.detail ?? 'Something went wrong. Try again.'}</Alert>
          </td>
        </tr>
      )}
    </>
  );
}
