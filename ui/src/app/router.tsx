import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { TeamsPage } from '../features/admin-users/TeamsPage';
import { AuditLogPage } from '../features/audit/AuditLogPage';
import { UsersPage } from '../features/admin-users/UsersPage';
import { LoginPage } from '../features/auth/LoginPage';
import { ProfilePage } from '../features/auth/ProfilePage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { SignUpPage } from '../features/auth/SignUpPage';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { RequireRole } from './RequireRole';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  { path: '/sign-up', element: <SignUpPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/me" replace /> },
          { path: 'me', element: <ProfilePage /> },
          {
            element: <RequireRole roles={['ADMIN', 'HR']} />,
            children: [
              { path: 'admin/users', element: <UsersPage /> },
              { path: 'admin/teams', element: <TeamsPage /> },
              { path: 'admin/audit', element: <AuditLogPage /> },
            ],
          },
        ],
      },
    ],
  },
  { element: <AppShell />, children: [{ path: '*', element: <NotFoundPage /> }] },
];

export const router = createBrowserRouter(routes);
