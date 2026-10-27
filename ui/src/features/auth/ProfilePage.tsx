import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../../shared/api/apiClient';
import { initials } from './initials';
import type { UserView } from './sessionStore';
import { useLogout } from './useLogout';

const roleLabels = { ADMIN: 'Admin', MANAGER: 'Manager', EMPLOYEE: 'Member' } as const;
const card = 'rounded-[14px] border border-line bg-surface p-7';
const secondaryButton =
  'h-11 cursor-pointer rounded-[10px] border border-line bg-transparent px-[18px] font-semibold text-ink';

export function ProfilePage() {
  const profile = useQuery({ queryKey: ['me'], queryFn: () => apiClient<UserView>('/api/v1/me') });

  return (
    <div className="mx-auto box-border flex w-full max-w-[720px] flex-col gap-6 px-4 py-12">
      <div className="flex flex-col gap-1">
        <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">Your profile</h1>
        <p className="m-0 text-muted">Your admin manages these details.</p>
      </div>
      {profile.isPending && <ProfileSkeleton />}
      {profile.isError && (
        <section role="alert" className={`${card} flex flex-col items-start gap-3.5`}>
          <span className="font-semibold text-danger-ink">We couldn't load your profile.</span>
          <p className="m-0 text-muted">Check your connection, then try again.</p>
          <button type="button" className={secondaryButton} onClick={() => profile.refetch()}>
            Try again
          </button>
        </section>
      )}
      {profile.data && <ProfileDetails user={profile.data} />}
    </div>
  );
}

function ProfileDetails({ user }: { user: UserView }) {
  const logout = useLogout();
  const role = user.role ? roleLabels[user.role] : '';
  return (
    <>
      <section className={`${card} flex flex-col gap-6`}>
        <div className="flex flex-wrap items-center gap-4">
          <div
            aria-hidden="true"
            className="flex size-16 items-center justify-center rounded-full bg-avatar-bg font-display text-[22px] font-bold text-avatar-ink shadow-[inset_0_-3px_0_var(--signal)]"
          >
            {initials(user.displayName ?? '')}
          </div>
          <div className="flex min-w-0 flex-col gap-0.5">
            <span className="font-display text-[22px] font-bold">{user.displayName}</span>
            <span className="text-muted">{role}</span>
          </div>
        </div>
        <dl className="m-0 grid grid-cols-[minmax(110px,max-content)_1fr] gap-x-6 gap-y-3.5 border-t border-line pt-[22px]">
          <dt className="text-sm text-muted">Work email</dt>
          <dd className="m-0 [overflow-wrap:anywhere]">{user.email}</dd>
          <dt className="text-sm text-muted">Company</dt>
          <dd className="m-0">{user.organizationName}</dd>
          <dt className="text-sm text-muted">Role</dt>
          <dd className="m-0">{role}</dd>
        </dl>
      </section>
      <section className="flex flex-wrap items-center justify-between gap-4 rounded-[14px] border border-line px-7 py-5">
        <div className="flex flex-col gap-0.5">
          <span className="font-semibold">Sign out</span>
          <span className="text-sm text-muted">Ends your session on this device.</span>
        </div>
        <button
          type="button"
          disabled={logout.isPending}
          onClick={() => logout.mutate()}
          className="h-11 cursor-pointer rounded-[10px] border-0 bg-accent px-[18px] font-semibold text-accent-ink disabled:opacity-75"
        >
          Sign out
        </button>
      </section>
    </>
  );
}

function ProfileSkeleton() {
  return (
    <section
      aria-busy="true"
      aria-label="Loading your profile"
      className={`${card} flex flex-col gap-[22px]`}
    >
      <div className="flex items-center gap-4">
        <div className="size-16 rounded-full bg-skeleton" />
        <div className="flex flex-1 flex-col gap-2.5">
          <div className="h-4 w-[45%] rounded-md bg-skeleton" />
          <div className="h-3 w-[30%] rounded-md bg-skeleton" />
        </div>
      </div>
      <div className="h-3 w-4/5 rounded-md bg-skeleton" />
      <div className="h-3 w-3/5 rounded-md bg-skeleton" />
    </section>
  );
}
