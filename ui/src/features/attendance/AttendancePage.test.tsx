import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSessionStore } from '../auth/sessionStore';
import { fetchCalls, stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const user = userEvent.setup();

const day = (date: string, extra: object = {}) => ({
  day: date,
  status: 'PRESENT',
  intervals: [{ from: `${date}T07:25:00Z`, to: `${date}T15:42:00Z` }],
  workedMinutes: 497,
  lateMinutes: 25,
  inNow: false,
  ...extra,
});

function signInAs(role: 'EMPLOYEE' | 'MANAGER' | 'HR') {
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role } });
}

beforeEach(() => resetSession());

it('punches in and out from the top bar, once per press', async () => {
  signInAs('EMPLOYEE');
  let punchedIn = false;
  stubApi((_method, url) => {
    if (url === '/api/v1/me/punch-in') {
      punchedIn = true;
      return [
        200,
        { punchedIn: true, since: '2026-11-04T07:25:00Z', lastOut: null, workedTodayMinutes: 0 },
      ];
    }
    if (url === '/api/v1/me/punch-out')
      return [
        200,
        { punchedIn: false, since: null, lastOut: '2026-11-04T15:42:00Z', workedTodayMinutes: 492 },
      ];
    if (url === '/api/v1/me/punch')
      return [
        200,
        {
          punchedIn,
          since: punchedIn ? '2026-11-04T07:25:00Z' : null,
          lastOut: null,
          workedTodayMinutes: 0,
        },
      ];
    return [200, []];
  }, true);
  renderApp('/attendance');

  const bar = await screen.findByRole('banner');
  await user.click(await within(bar).findByRole('button', { name: 'Punch in' }));
  expect(await within(bar).findByText(/^In since/)).toBeInTheDocument();
  expect(within(bar).getByText(/^\d+:\d\d:\d\d$/)).toBeInTheDocument();
  const punchIn = fetchCalls().find((call) => call.url === '/api/v1/me/punch-in');
  expect(new Headers(punchIn?.init.headers).get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);

  await user.click(within(bar).getByRole('button', { name: 'Punch out' }));
  expect(await within(bar).findByRole('status')).toHaveTextContent(
    /Punched out at \d\d:\d\d\.You worked 8h 12m today\./,
  );
});

it('shows my week with late minutes, intervals, absences and weekends', async () => {
  signInAs('EMPLOYEE');
  stubApi((_method, url) => {
    if (url.startsWith('/api/v1/me/attendance'))
      return [
        200,
        [
          day('2026-11-04'),
          day('2026-11-03', { status: 'ABSENT', intervals: [], workedMinutes: 0, lateMinutes: 0 }),
          day('2026-10-31', { status: 'WEEKEND', intervals: [], workedMinutes: 0, lateMinutes: 0 }),
        ],
      ];
    return [200, []];
  });
  renderApp('/attendance');

  expect(await screen.findByRole('heading', { name: 'My attendance' })).toBeInTheDocument();
  expect(screen.queryByRole('tab')).not.toBeInTheDocument();
  expect(await screen.findByText('Late 25m')).toBeInTheDocument();
  expect(screen.getByText('Absent')).toBeInTheDocument();
  expect(screen.getByText('Weekend')).toBeInTheDocument();
  expect(screen.getAllByText('No attendance for this day.')).toHaveLength(2);
  expect(screen.getByText('Days present')).toBeInTheDocument();
  expect(screen.getByRole('heading', { name: 'Today' })).toBeInTheDocument();
  expect(screen.getByText('1 of 2')).toBeInTheDocument();
});

it('opens the team view for managers without asking for the team list', async () => {
  signInAs('MANAGER');
  stubApi((_method, url) => {
    if (url.startsWith('/api/v1/attendance'))
      return [
        200,
        {
          items: [
            {
              userId: 'u1',
              displayName: 'Nour Hassan',
              teamName: 'Product',
              day: day('2026-11-04', { inNow: true }),
            },
          ],
          page: 0,
          size: 20,
          totalItems: 1,
          totalPages: 1,
        },
      ];
    return [200, []];
  });
  renderApp('/attendance');

  expect(await screen.findByRole('heading', { name: 'Team attendance' })).toBeInTheDocument();
  const row = (await screen.findByText('Nour Hassan')).closest('tr')!;
  expect(within(row).getByText('In now')).toBeInTheDocument();
  expect(within(row).getByText('Late 25m')).toBeInTheDocument();
  expect(screen.queryByRole('combobox', { name: 'Team' })).not.toBeInTheDocument();
  expect(fetchCalls().some((call) => call.url.startsWith('/api/v1/teams'))).toBe(false);

  await user.click(screen.getByRole('tab', { name: 'My attendance' }));
  expect(await screen.findByRole('heading', { name: 'My attendance' })).toBeInTheDocument();
});
