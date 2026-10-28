import { Outlet } from 'react-router';
import { useSessionStore } from '../features/auth/sessionStore';
import type { Role } from '../shared/roles';
import { NotFoundPage } from './NotFoundPage';

// People without the role get the not-found page, so the screen's existence isn't revealed.
export function RequireRole({ roles }: { roles: Role[] }) {
  const role = useSessionStore((state) => state.user?.role);
  return role && roles.includes(role) ? <Outlet /> : <NotFoundPage />;
}
