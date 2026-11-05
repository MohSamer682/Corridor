import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSessionStore } from '../auth/sessionStore';
import { useConversationStore } from '../talk/conversationStore';
import { fetchCalls, stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';

const user = userEvent.setup();
const note = (id: string, body: string, teamName: string | null = null) => ({
  id,
  body,
  teamName,
  authorId: 'salma',
  authorName: 'Salma Ibrahim',
  createdAt: '2026-11-05T10:00:00Z',
});

function signInAs(role: 'EMPLOYEE' | 'MANAGER') {
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role } });
  return useSessionStore.getState().user!.id!;
}

beforeEach(() => {
  resetSession();
  useConversationStore.getState().reset();
});

it('shows unread announcements one at a time and dismisses them', async () => {
  signInAs('EMPLOYEE');
  let unread = [
    note('a1', 'Release freeze starts at 5 pm.', 'Backend'),
    note('a2', 'Lunch is on us.'),
  ];
  stubApi((method, url) => {
    if (url === '/api/v1/announcements/unread') return [200, unread];
    if (method === 'POST' && url === '/api/v1/announcements/a1/read') {
      unread = unread.slice(1);
      return [200, { id: 'a1', status: 'READ' }];
    }
    return [200, url === '/api/v1/town-hall' ? null : {}];
  }, true);
  renderApp('/me');

  const banner = await screen.findByRole('region', { name: 'Announcement' });
  expect(banner).toHaveTextContent('Release freeze starts at 5 pm.');
  expect(banner).toHaveTextContent('Salma Ibrahim · to Backend');
  expect(banner).toHaveTextContent('1 more');
  expect(screen.queryByRole('button', { name: 'Broadcast' })).not.toBeInTheDocument();

  await user.click(within(banner).getByRole('button', { name: 'Next' }));
  expect(await screen.findByText('Lunch is on us.')).toBeInTheDocument();
});

it('waits while you are in focus mode', async () => {
  const me = signInAs('EMPLOYEE');
  useConversationStore.getState().setFocused([me]);
  stubApi(
    (_method, url) =>
      url === '/api/v1/announcements/unread' ? [200, [note('a1', 'Hello')]] : [200, null],
    true,
  );
  renderApp('/me');

  await screen.findByRole('heading', { name: /profile/i });
  expect(screen.queryByRole('region', { name: 'Announcement' })).not.toBeInTheDocument();
});

it('invites everyone to a live town hall', async () => {
  signInAs('EMPLOYEE');
  stubApi((_method, url) => {
    if (url === '/api/v1/town-hall')
      return [
        200,
        {
          title: 'Q4 all-hands',
          floorId: 'g',
          x: 11.5,
          y: 9.5,
          roomName: 'Town Hall',
          calledBy: 's',
          startedAt: 'now',
        },
      ];
    if (url === '/api/v1/announcements/unread') return [200, []];
    return [200, url === '/api/v1/town-hall' ? null : {}];
  }, true);
  renderApp('/me');

  const hall = await screen.findByRole('region', { name: 'Town hall' });
  expect(hall).toHaveTextContent('Q4 all-hands is on now in the Town Hall.');
  await user.click(within(hall).getByRole('button', { name: 'Not now' }));
  expect(screen.queryByRole('region', { name: 'Town hall' })).not.toBeInTheDocument();
});

it('lets a manager broadcast and says how many it reached', async () => {
  signInAs('MANAGER');
  stubApi((method, url) => {
    if (method === 'POST' && url === '/api/v1/announcements')
      return [200, { id: 'n1', audience: 18, online: 12 }];
    if (url === '/api/v1/announcements/unread') return [200, []];
    return [200, url === '/api/v1/town-hall' ? null : {}];
  }, true);
  renderApp('/me');

  await user.click(await screen.findByRole('button', { name: 'Broadcast' }));
  const sheet = await screen.findByRole('dialog', { name: 'Broadcast' });
  await user.click(within(sheet).getByRole('button', { name: 'Send' }));
  expect(within(sheet).getByText('Write something to send.')).toBeInTheDocument();

  await user.type(within(sheet).getByLabelText('Message'), 'All hands at 3.');
  await user.click(within(sheet).getByRole('button', { name: 'Send' }));

  expect(
    await within(sheet).findByText(
      'Sent to 18 people. 12 online now; the rest see it when they open Corridor.',
    ),
  ).toBeInTheDocument();
  const post = fetchCalls().find(
    (c) => c.init.method === 'POST' && c.url === '/api/v1/announcements',
  );
  expect(JSON.parse(String(post?.init.body))).toEqual({ teamId: null, body: 'All hands at 3.' });
});
