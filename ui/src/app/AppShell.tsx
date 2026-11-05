import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router';
import { AnnouncementBanner } from '../features/announcements/AnnouncementBanner';
import { BroadcastSheet } from '../features/announcements/BroadcastSheet';
import { PunchClock } from '../features/attendance/PunchClock';
import { initials } from '../features/auth/initials';
import { useSessionStore } from '../features/auth/sessionStore';
import { BrandMark } from './BrandMark';

const adminLinks = [
  ['/admin/users', 'People'],
  ['/admin/teams', 'Teams'],
  ['/admin/audit', 'Audit log'],
] as const;

export function AppShell() {
  const user = useSessionStore((state) => state.user);
  const [broadcasting, setBroadcasting] = useState(false);
  const canBroadcast = user?.role === 'MANAGER' || user?.role === 'HR' || user?.role === 'ADMIN';
  const links = user
    ? ([
        ['/office', 'Office'],
        ...(canBroadcast ? ([['/command-center', 'Command Center']] as const) : []),
        ['/meetings', 'Meetings'],
        ['/attendance', 'Attendance'],
        ['/me', 'Profile'],
        ...(user.role === 'ADMIN' || user.role === 'HR' ? adminLinks : []),
        ...(user.role === 'ADMIN' ? ([['/admin/jira', 'Jira']] as const) : []),
      ] as const)
    : [];
  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only rounded-md bg-surface px-4 py-2 focus:not-sr-only focus:absolute focus:top-2 focus:left-2"
      >
        Skip to content
      </a>
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-line bg-surface px-[max(16px,3%)] py-3">
        <Link to="/" className="flex min-h-11 items-center text-ink no-underline">
          <BrandMark />
        </Link>
        {user && (
          <nav
            aria-label="Main"
            className={`order-3 flex w-full gap-1 overflow-x-auto ${
              // Admins have nine links; below 1536px they get their own row instead of squeezing the top bar.
              links.length > 6 ? '2xl:order-none 2xl:w-auto' : 'sm:order-none sm:w-auto'
            }`}
          >
            {links.map(([to, text]) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `flex min-h-11 items-center border-b-2 px-3 font-medium whitespace-nowrap no-underline ${
                    isActive ? 'border-accent text-ink' : 'border-transparent text-muted'
                  }`
                }
              >
                {text}
              </NavLink>
            ))}
          </nav>
        )}
        {user && (
          <div className="flex items-center gap-3">
            {canBroadcast && (
              <button
                type="button"
                onClick={() => setBroadcasting(true)}
                className="hidden h-10 cursor-pointer items-center rounded-[10px] border border-line bg-transparent px-3.5 text-sm font-semibold text-ink hover:bg-field sm:inline-flex"
              >
                Broadcast
              </button>
            )}
            <PunchClock />
            <span
              title={user.displayName}
              className="flex size-11 items-center justify-center rounded-full border border-line bg-avatar-bg text-sm font-semibold text-avatar-ink"
            >
              {initials(user.displayName ?? '')}
            </span>
          </div>
        )}
      </header>
      {user && <AnnouncementBanner />}
      {canBroadcast && (
        <BroadcastSheet open={broadcasting} onClose={() => setBroadcasting(false)} />
      )}
      <main id="main" className="flex flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  );
}
