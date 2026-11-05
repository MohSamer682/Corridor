import { Navigate, Outlet } from 'react-router';
import { useSessionStore } from '../features/auth/sessionStore';
import type { Role } from '../shared/roles';
import { NotFoundPage } from './NotFoundPage';

// People without the role get the not-found page, so the screen's existence isn't revealed, unless the
// screen is known to everyone and simply not theirs (the Command Center): then they go to `redirectTo`.
export function RequireRole({ roles, redirectTo }: { roles: Role[]; redirectTo?: string }) {
  const role = useSessionStore((state) => state.user?.role);
  if (role && roles.includes(role)) return <Outlet />;
  return redirectTo ? <Navigate to={redirectTo} replace /> : <NotFoundPage />;
}
