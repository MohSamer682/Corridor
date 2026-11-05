import { createBrowserRouter, Navigate, type RouteObject } from 'react-router';
import { TeamsPage } from '../features/admin-users/TeamsPage';
import { AttendancePage } from '../features/attendance/AttendancePage';
import { AuditLogPage } from '../features/audit/AuditLogPage';
import { MeetingsPage } from '../features/meetings/MeetingsPage';
import { OfficePage } from '../features/office/OfficePage';
import { UsersPage } from '../features/admin-users/UsersPage';
import { LoginPage } from '../features/auth/LoginPage';
import { ProfilePage } from '../features/auth/ProfilePage';
import { RequireAuth } from '../features/auth/RequireAuth';
import { SignUpPage } from '../features/auth/SignUpPage';
import { JiraConnectionPage } from '../features/workboard/JiraConnectionPage';
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
          { index: true, element: <Navigate to="/office" replace /> },
          { path: 'office', element: <OfficePage /> },
          { path: 'office/buildings/:buildingId', element: <OfficePage /> },
          { path: 'office/floors/:floorId', element: <OfficePage /> },
          { path: 'me', element: <ProfilePage /> },
          { path: 'attendance', element: <AttendancePage /> },
          { path: 'meetings', element: <MeetingsPage /> },
          {
            element: <RequireRole roles={['ADMIN', 'HR']} />,
            children: [
              { path: 'admin/users', element: <UsersPage /> },
              { path: 'admin/teams', element: <TeamsPage /> },
              { path: 'admin/audit', element: <AuditLogPage /> },
            ],
          },
          {
            element: <RequireRole roles={['ADMIN']} />,
            children: [{ path: 'admin/jira', element: <JiraConnectionPage /> }],
          },
        ],
      },
    ],
  },
  { element: <AppShell />, children: [{ path: '*', element: <NotFoundPage /> }] },
];

export const router = createBrowserRouter(routes);
