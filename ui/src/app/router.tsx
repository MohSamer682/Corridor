import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { LoginPage } from '../features/auth/LoginPage';
import { ProfilePage } from '../features/auth/ProfilePage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';

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
        ],
      },
    ],
  },
  { element: <AppShell />, children: [{ path: '*', element: <NotFoundPage /> }] },
];

export const router = createBrowserRouter(routes);
