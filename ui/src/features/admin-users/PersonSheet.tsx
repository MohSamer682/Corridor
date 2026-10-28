import { zodResolver } from '@hookform/resolvers/zod';
import { forwardRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { apiClient } from '../../shared/api/apiClient';
import { roleLabels, type Role } from '../../shared/roles';
import { Alert } from '../../shared/ui/Alert';
import { Sheet } from '../../shared/ui/Sheet';
import { ifMatch, useAdminMutation, usePerson, type TeamView, type UserView } from './usePeople';

const field =
  'h-11 w-full rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none aria-[invalid=true]:border-danger-ink';
const label = 'mb-1.5 block text-[13px] font-semibold';
const secondary =
  'h-11 cursor-pointer rounded-[10px] border border-line bg-transparent px-[18px] font-semibold text-ink disabled:opacity-60';

const details = {
  displayName: z
    .string()
    .trim()
    .min(2, 'Use 2 to 80 characters.')
    .max(80, 'Use 2 to 80 characters.'),
  jobTitle: z.string().trim().max(80, 'Use up to 80 characters.'),
  role: z.enum(['ADMIN', 'MANAGER', 'EMPLOYEE']),
  teamId: z.string(),
};
const editSchema = z.object(details);
const addSchema = z.object({
  ...details,
  email: z.string().trim().max(254, 'Enter a work email.').pipe(z.email('Enter a work email.')),
  password: z.string().min(8, 'Use 8 to 128 characters.').max(128, 'Use 8 to 128 characters.'),
});
type PersonValues = z.infer<typeof addSchema>;

type SheetState = { mode: 'add' } | { mode: 'edit'; id: string } | null;

export function PersonSheet({
  state,
  teams,
  onClose,
}: {
  state: SheetState;
  teams: TeamView[];
  onClose: () => void;
}) {
  const person = usePerson(state?.mode === 'edit' ? state.id : null);
  const editing = state?.mode === 'edit';
  const title = editing ? (person.data?.displayName ?? 'Loading…') : 'Add person';
  return (
    <Sheet
      open={state !== null}
      onOpenChange={(open) => !open && onClose()}
      title={title}
      description={editing ? person.data?.email : undefined}
    >
      {state?.mode === 'add' && <PersonForm teams={teams} onDone={onClose} />}
      {editing && person.data && (
        <PersonForm key={person.data.version} person={person.data} teams={teams} onDone={onClose} />
      )}
    </Sheet>
  );
}

function PersonForm({
  person,
  teams,
  onDone,
}: {
  person?: UserView;
  teams: TeamView[];
  onDone: () => void;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<PersonValues>({
    resolver: zodResolver(person ? editSchema : addSchema) as never,
    defaultValues: {
      displayName: person?.displayName ?? '',
      jobTitle: person?.jobTitle ?? '',
      role: (person?.role ?? 'EMPLOYEE') as Role,
      teamId: person?.teamId ?? '',
      email: '',
      password: '',
    },
  });
  const save = useAdminMutation((values: PersonValues) => {
    const body = { ...values, jobTitle: values.jobTitle || null, teamId: values.teamId || null };
    return person
      ? apiClient(`/api/v1/users/${person.id}`, {
          method: 'PATCH',
          headers: ifMatch(person.version),
          body: JSON.stringify(body),
        })
      : apiClient('/api/v1/users', { method: 'POST', body: JSON.stringify(body) });
  });

  return (
    <form
      noValidate
      className="flex min-h-0 flex-1 flex-col"
      onSubmit={handleSubmit((values) => save.mutate(values, { onSuccess: onDone }))}
    >
      <div className="flex flex-1 flex-col gap-[18px] overflow-auto px-7 py-5">
        {save.error && (
          <Alert tone="danger">{save.error.detail ?? 'Something went wrong. Try again.'}</Alert>
        )}
        <TextField
          id="displayName"
          text="Name"
          error={errors.displayName?.message}
          {...register('displayName')}
        />
        {!person && (
          <TextField
            id="email"
            text="Work email"
            type="email"
            error={errors.email?.message}
            {...register('email')}
          />
        )}
        <TextField
          id="jobTitle"
          text="Job title"
          error={errors.jobTitle?.message}
          {...register('jobTitle')}
        />
        <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2">
          <div>
            <label className={label} htmlFor="role">
              Role
            </label>
            <select id="role" className={field} {...register('role')}>
              {Object.entries(roleLabels).map(([value, text]) => (
                <option key={value} value={value}>
                  {text}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className={label} htmlFor="teamId">
              Team
            </label>
            <select id="teamId" className={field} {...register('teamId')}>
              <option value="">No team</option>
              {teams.map((team) => (
                <option key={team.id} value={team.id}>
                  {team.name}
                </option>
              ))}
            </select>
          </div>
        </div>
        {!person && (
          <TextField
            id="password"
            text="First password"
            type="password"
            hint="Share it with them directly. Use 8 to 128 characters."
            error={errors.password?.message}
            {...register('password')}
          />
        )}
        {person && <AccessZone person={person} onRemoved={onDone} />}
      </div>
      <div className="flex justify-end gap-2.5 border-t border-line px-7 py-4">
        <button type="button" className={secondary} onClick={onDone}>
          Cancel
        </button>
        <button
          type="submit"
          disabled={save.isPending}
          className="h-11 cursor-pointer rounded-[10px] border-0 bg-accent px-[18px] font-semibold text-accent-ink disabled:opacity-75"
        >
          {person ? 'Save changes' : 'Add person'}
        </button>
      </div>
    </form>
  );
}

type TextFieldProps = React.InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  text: string;
  error?: string;
  hint?: string;
};

const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { id, text, error, hint, ...input },
  ref,
) {
  return (
    <div>
      <label className={label} htmlFor={id}>
        {text}
      </label>
      <input
        id={id}
        className={field}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
        ref={ref}
        {...input}
      />
      {hint && !error && (
        <p id={`${id}-hint`} className="m-0 mt-1.5 text-[13px] text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="m-0 mt-1.5 text-[13px] text-danger-ink">
          {error}
        </p>
      )}
    </div>
  );
});

function AccessZone({ person, onRemoved }: { person: UserView; onRemoved: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const suspended = person.status === 'SUSPENDED';
  const firstName = (person.displayName ?? '').split(' ')[0];
  const access = useAdminMutation(() =>
    apiClient(`/api/v1/users/${person.id}/suspension`, { method: suspended ? 'DELETE' : 'POST' }),
  );
  const remove = useAdminMutation(() =>
    apiClient(`/api/v1/users/${person.id}`, { method: 'DELETE' }),
  );
  const error = access.error ?? remove.error;

  return (
    <section className="flex flex-col gap-3 rounded-[14px] border border-line px-[18px] py-4">
      {error && <Alert tone="danger">{error.detail ?? 'Something went wrong. Try again.'}</Alert>}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <span className="block font-semibold">Access</span>
          <span className="block text-sm text-muted">
            {suspended ? `Suspended. ${firstName} can't sign in.` : 'Active. Can sign in.'}
          </span>
        </div>
        <button
          type="button"
          className={secondary}
          disabled={access.isPending}
          onClick={() => access.mutate(undefined)}
        >
          {suspended ? 'Reactivate' : 'Suspend'}
        </button>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line pt-3">
        <div>
          <span className="block font-semibold">Remove from Corridor</span>
          <span className="block text-sm text-muted">Deletes the account.</span>
        </div>
        {!confirming && (
          <button type="button" className={secondary} onClick={() => setConfirming(true)}>
            Remove from Corridor
          </button>
        )}
      </div>
      {confirming && (
        <div
          role="group"
          aria-label="Confirm removal"
          className="flex flex-col gap-3 rounded-[10px] bg-danger-bg p-3.5 text-danger-ink"
        >
          <span>
            Remove {person.displayName}? They lose access right away, and this can't be undone.
          </span>
          <div className="flex gap-2.5">
            <button
              type="button"
              disabled={remove.isPending}
              onClick={() => remove.mutate(undefined, { onSuccess: onRemoved })}
              className="h-11 cursor-pointer rounded-[10px] border-0 bg-danger-ink px-[18px] font-semibold text-surface"
            >
              Remove
            </button>
            <button
              type="button"
              onClick={() => setConfirming(false)}
              className="h-11 cursor-pointer rounded-[10px] border border-current bg-transparent px-[18px] font-semibold text-inherit"
            >
              Keep
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
