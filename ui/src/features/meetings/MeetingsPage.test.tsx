import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSessionStore } from '../auth/sessionStore';
import { fetchCalls, stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const user = userEvent.setup();
const minutes = (n: number) => new Date(Date.now() + n * 60_000).toISOString();

function meeting(id: string, title: string, start: number, extra: object = {}) {
  return {
    id,
    title,
    roomId: 'nile',
    roomName: 'Nile Room',
    place: 'Corridor HQ · L1',
    floorId: 'l1',
    startsAt: minutes(start),
    endsAt: minutes(start + 30),
    status: 'SCHEDULED',
    organizerId: 'someone-else',
    attendees: ['a', 'b'],
    ...extra,
  };
}

function signIn() {
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role: 'EMPLOYEE' } });
  return useSessionStore.getState().user!.id!;
}

beforeEach(() => resetSession());

it('lists meetings with Join only when it is open, and lets the organizer cancel', async () => {
  const me = signIn();
  stubApi((method, url) => {
    if (url.startsWith('/api/v1/meetings?'))
      return [
        200,
        [
          meeting('m1', 'Release planning', -5, { status: 'LIVE' }),
          meeting('m2', 'Design review', 5),
          meeting('m3', 'Hiring sync', 120, { organizerId: me }),
          meeting('m4', 'Vendor call', 240, { status: 'CANCELLED' }),
        ],
      ];
    if (method === 'POST') return [200, { id: 'm3', status: 'CANCELLED' }];
    return [200, []];
  });
  renderApp('/meetings');

  expect(await screen.findByRole('button', { name: 'Join Release planning' })).toBeInTheDocument();
  expect(screen.getByText('Live now')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Join Design review' })).toBeInTheDocument();
  expect(screen.getByText(/^Starts in [56] min$/)).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Join Hiring sync' })).not.toBeInTheDocument();
  expect(screen.getByText('Cancelled')).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Join Vendor call' })).not.toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: 'Cancel meeting Hiring sync' }));
  expect(fetchCalls().some((c) => c.url === '/api/v1/meetings/m3/cancellation')).toBe(true);
});

it('explains an empty day', async () => {
  signIn();
  stubApi(() => [200, []]);
  renderApp('/meetings');

  expect(await screen.findByRole('heading', { name: 'No meetings today.' })).toBeInTheDocument();
});

it('checks the form, then shows a clash next to the room', async () => {
  signIn();
  stubApi((method, url) => {
    if (url === '/api/v1/meeting-rooms')
      return [200, [{ id: 'nile', name: 'Nile Room', floorId: 'l1', place: 'Corridor HQ · L1' }]];
    if (url === '/api/v1/meeting-invitees')
      return [200, [{ userId: 'omar', displayName: 'Omar Adel', teamId: null, teamName: null }]];
    if (method === 'POST' && url === '/api/v1/meetings')
      return [
        409,
        {
          status: 409,
          code: 'CONFLICT',
          detail: 'The Nile Room is booked then for “Release planning”. Pick another room or time.',
        },
      ];
    return [200, []];
  });
  renderApp('/meetings');

  await user.click(await screen.findByRole('button', { name: 'Schedule a meeting' }));
  const sheet = await screen.findByRole('dialog', { name: 'Schedule a meeting' });
  await user.click(within(sheet).getByRole('button', { name: 'Schedule' }));
  expect(within(sheet).getByText('Use 1 to 120 characters.')).toBeInTheDocument();
  expect(within(sheet).getByText('Pick a room.')).toBeInTheDocument();

  await user.type(within(sheet).getByLabelText('Title'), 'Roadmap check-in');
  await user.selectOptions(await within(sheet).findByLabelText('Room'), 'nile');
  await user.click(within(sheet).getByRole('checkbox', { name: /Omar Adel/ }));
  await user.click(within(sheet).getByRole('button', { name: 'Schedule' }));

  expect(
    await within(sheet).findByText(
      'The Nile Room is booked then for “Release planning”. Pick another room or time.',
    ),
  ).toBeInTheDocument();
  const post = fetchCalls().find((c) => c.init.method === 'POST' && c.url === '/api/v1/meetings');
  expect(JSON.parse(String(post?.init.body))).toMatchObject({
    roomId: 'nile',
    title: 'Roadmap check-in',
    minutes: 30,
    invitees: ['omar'],
  });
  expect(new Headers(post?.init.headers).get('Idempotency-Key')).toBeTruthy();
});
