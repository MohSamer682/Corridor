import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { TeamsPage } from '../features/admin-users/TeamsPage';
import { UsersPage } from '../features/admin-users/UsersPage';
import { LoginPage } from '../features/auth/LoginPage';
import { ProfilePage } from '../features/auth/ProfilePage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';
import { RequireRole } from './RequireRole';

export const routes: RouteObject[] = [
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/me" replace /> },
          { path: 'me', element: <ProfilePage /> },
          {
            element: <RequireRole roles={['ADMIN']} />,
            children: [
              { path: 'admin/users', element: <UsersPage /> },
              { path: 'admin/teams', element: <TeamsPage /> },
            ],
          },
        ],
      },
    ],
  },
  { element: <AppShell />, children: [{ path: '*', element: <NotFoundPage /> }] },
];

export const router = createBrowserRouter(routes);
