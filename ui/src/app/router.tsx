import { createBrowserRouter } from 'react-router';
import { AppShell } from './AppShell';
import { NotFoundPage } from './NotFoundPage';

export const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [{ path: '*', element: <NotFoundPage /> }],
  },
]);
