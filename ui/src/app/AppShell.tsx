import { Link, NavLink, Outlet } from 'react-router';
import { initials } from '../features/auth/initials';
import { useSessionStore } from '../features/auth/sessionStore';
import { BrandMark } from './BrandMark';

export function AppShell() {
  const user = useSessionStore((state) => state.user);
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
        {user?.role === 'ADMIN' && (
          <nav
            aria-label="Main"
            className="order-3 flex w-full gap-1 overflow-x-auto sm:order-none sm:w-auto"
          >
            {(
              [
                ['/me', 'Profile'],
                ['/admin/users', 'People'],
                ['/admin/teams', 'Teams'],
              ] as const
            ).map(([to, text]) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  `flex min-h-11 items-center border-b-2 px-3 font-medium no-underline ${
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
            <span className="text-sm text-muted">{user.organizationName}</span>
            <span
              title={user.displayName}
              className="flex size-11 items-center justify-center rounded-full border border-line bg-avatar-bg text-sm font-semibold text-avatar-ink"
            >
              {initials(user.displayName ?? '')}
            </span>
          </div>
        )}
      </header>
      <main id="main" className="flex flex-1 flex-col">
        <Outlet />
      </main>
    </div>
  );
}
