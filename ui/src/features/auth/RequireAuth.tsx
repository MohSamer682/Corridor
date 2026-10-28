import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { refreshOnce } from '../../shared/api/apiClient';
import { useSessionStore } from './sessionStore';

export function RequireAuth() {
  const accessToken = useSessionStore((state) => state.accessToken);
  const location = useLocation();
  const [restoreTried, setRestoreTried] = useState(accessToken !== null);

  useEffect(() => {
    if (accessToken || restoreTried) return;
    refreshOnce()
      .catch(() => undefined)
      .finally(() => setRestoreTried(true));
  }, [accessToken, restoreTried]);

  if (accessToken) return <Outlet />;
  if (!restoreTried)
    return <div className="min-h-screen bg-bg" aria-busy="true" aria-label="Opening Corridor" />;
  return <Navigate to="/login" replace state={{ from: location.pathname }} />;
}
