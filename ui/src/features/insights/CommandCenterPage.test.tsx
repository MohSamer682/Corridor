import { screen, within } from '@testing-library/react';
import { useSessionStore } from '../auth/sessionStore';
import { stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const campus = {
  buildings: [
    {
      id: 'hq',
      name: 'Corridor HQ',
      campusX: 0,
      campusZ: 0,
      floors: [
        { id: 'g', level: 0, code: 'G', name: 'Lobby & Commons' },
        { id: 'l1', level: 1, code: 'L1', name: 'Engineering' },
      ],
    },
  ],
};
const days = Array.from({ length: 14 }, (_, i) => {
  const day = new Date(Date.UTC(2026, 9, 23 + i)).toISOString().slice(0, 10);
  const weekday = new Date(day).getUTCDay();
  return {
    day,
    present: weekday === 5 || weekday === 6 ? 0 : 10 + i,
    weekend: weekday === 5 || weekday === 6,
  };
});
const snapshot = {
  at: '2026-11-05T08:00:00Z',
  scope: 'Backend',
  kpis: {
    people: 12,
    online: 7,
    inMeetings: 3,
    inFocus: 2,
    punchedIn: 9,
    late: 1,
    roomsInUse: 1,
    rooms: 4,
  },
  floors: [{ floorId: 'l1', online: 7 }],
  rooms: [
    {
      roomId: 'nile',
      name: 'Nile Room',
      place: 'Corridor HQ · L1',
      meeting: 'Sprint review',
      people: 8,
      capacity: 10,
    },
    {
      roomId: 'siwa',
      name: 'Siwa Room',
      place: 'Corridor HQ · L2',
      meeting: null,
      people: 0,
      capacity: 6,
    },
  ],
  teams: [{ team: 'Backend', people: 12, online: 7, punchedIn: 9, late: 1, inMeetings: 3 }],
  attention: ['Nadia has not clocked in.', 'Nile Room is at 80% capacity.'],
  days,
};

function signInAs(role: 'MANAGER' | 'EMPLOYEE') {
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role } });
}

beforeEach(() => resetSession());

it('shows a manager their team at a glance', async () => {
  signInAs('MANAGER');
  stubApi((_method, url) => {
    if (url === '/api/v1/command-center') return [200, snapshot];
    if (url === '/api/v1/campus') return [200, campus];
    return [200, {}];
  });
  renderApp('/command-center');

  expect(await screen.findByRole('heading', { name: 'Command Center' })).toBeInTheDocument();
  expect(await screen.findByText('Backend · 12 people')).toBeInTheDocument();
  const kpis = screen.getByRole('list', { name: 'Right now' });
  expect(within(kpis).getByText('Online').parentElement).toHaveTextContent('7');
  expect(within(kpis).getByText('Rooms in use').parentElement).toHaveTextContent('1 of 4');

  const attention = screen.getByRole('region', { name: 'Needs attention' });
  expect(
    within(attention)
      .getAllByRole('listitem')
      .map((li) => li.textContent),
  ).toEqual(['Nadia has not clocked in.', 'Nile Room is at 80% capacity.']);
  const rooms = screen.getByRole('region', { name: 'Rooms' });
  expect(within(rooms).getByText('Sprint review · 8 of 10 seats')).toBeInTheDocument();
  expect(within(rooms).getByText('Free')).toBeInTheDocument();
  const floors = screen.getByRole('region', { name: 'Floors' });
  expect(await within(floors).findByText('L1 · Engineering')).toBeInTheDocument();
  expect(within(floors).getByText('G · Lobby & Commons')).toBeInTheDocument();

  const teams = screen.getByRole('table', { name: 'Teams' });
  expect(within(teams).getByRole('row', { name: /Backend/ })).toHaveTextContent('Backend127913');
  const history = screen.getByRole('table', { name: 'Punched in, last 14 days' });
  expect(within(history).getAllByRole('row')).toHaveLength(15);
});

it('sends employees back to the office and hides the link', async () => {
  signInAs('EMPLOYEE');
  stubApi((_method, url) => [200, url === '/api/v1/campus' ? campus : {}]);
  const router = renderApp('/command-center');

  await screen.findByRole('navigation', { name: 'Main' });
  expect(router.state.location.pathname).toBe('/office');
  expect(screen.queryByRole('link', { name: 'Command Center' })).not.toBeInTheDocument();
});
