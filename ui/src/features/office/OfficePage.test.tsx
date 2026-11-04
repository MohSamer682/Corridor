import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSessionStore } from '../auth/sessionStore';
import { stubApi, tokenResponse } from '../../test/fetchStub';
import { renderApp, resetSession } from '../../test/renderApp';
import { useConversationStore } from '../talk/conversationStore';
import { usePresenceStore } from './presence/presenceStore';
import { hasWebGL } from './webgl';

vi.mock('./webgl', () => ({ hasWebGL: vi.fn(() => true) }));
const walk = vi.fn();
const send = vi.fn();
vi.mock('./presence/usePresence', () => ({ usePresence: () => ({ move: walk, send }) }));
const walkTo = vi.fn();
vi.mock('./OfficeScene', async () => {
  const { useEffect } = await import('react');
  return {
    OfficeScene: ({ view, onReady }: { view: { mode: string }; onReady: (c: object) => void }) => {
      useEffect(
        () => onReady({ rotate: vi.fn(), zoomBy: vi.fn(), lookAt: vi.fn(), walkTo }),
        [onReady],
      );
      return <div data-testid="scene">{view.mode}</div>;
    },
  };
});

const user = userEvent.setup();
const floor = (id: string, level: number, code: string, name: string) => ({
  id,
  level,
  code,
  name,
  details: `${name} details`,
});
const campus = {
  buildings: [
    {
      id: 'hq',
      name: 'Corridor HQ',
      subtitle: 'Head office',
      campusX: -30,
      campusZ: -12,
      floors: [floor('g', 0, 'G', 'Lobby & Commons'), floor('l1', 1, 'L1', 'Engineering')],
    },
    {
      id: 'lab',
      name: 'Innovation Lab',
      subtitle: 'QA & design studio',
      campusX: 22,
      campusZ: 2,
      floors: [floor('lab0', 0, 'G', 'QA Lab & Design Studio')],
    },
  ],
};
const layout = {
  floorId: 'l1',
  version: 1,
  width: 3,
  height: 3,
  arrival: { x: 1, y: 1 },
  tiles: 'CCCC.CCCC',
  furniture: {},
  rooms: [],
  seats: [],
};

function signInAs(role: 'ADMIN' | 'EMPLOYEE') {
  const token = tokenResponse('t');
  useSessionStore.getState().signIn({ ...token, user: { ...token.user, role } });
}

let desk: unknown = null;

function serveCampus(body: unknown = campus, status = 200) {
  stubApi((_method, url) => {
    if (url.includes('/layout')) return [200, layout];
    if (url.includes('/presence/counts')) return [200, { floors: { l1: 4, g: 1 } }];
    if (url.includes('/me/desk')) return [200, { desk }];
    return [status, body];
  });
}

beforeEach(() => {
  resetSession();
  desk = null;
  walk.mockReset();
  usePresenceStore.getState().reset();
  useConversationStore.getState().reset();
  send.mockReset();
  signInAs('EMPLOYEE');
  vi.mocked(hasWebGL).mockReturnValue(true);
});

it('opens the office first and shows employees only Office and Profile', async () => {
  serveCampus();
  renderApp('/');

  const nav = await screen.findByRole('navigation', { name: 'Main' });
  expect(within(nav).getByRole('link', { name: 'Office' })).toHaveAttribute('aria-current', 'page');
  expect(within(nav).getByRole('link', { name: 'Profile' })).toBeInTheDocument();
  expect(within(nav).queryByRole('link', { name: 'People' })).not.toBeInTheDocument();
});

it('shows the campus with its buildings', async () => {
  serveCampus();
  renderApp('/office');

  expect(await screen.findByTestId('scene')).toHaveTextContent('campus');
  expect(screen.getByRole('heading', { name: 'Your campus' })).toBeInTheDocument();
  expect(
    await screen.findByText(
      '2 buildings · 3 floors · 5 people here now. Click a building to look inside.',
    ),
  ).toBeInTheDocument();
});

it('opens a building from the campus card and lists its floors from the top down', async () => {
  serveCampus();
  renderApp('/office');

  await user.click(await screen.findByRole('button', { name: 'Enter Corridor HQ' }));

  const rail = await screen.findByRole('navigation', { name: 'Floors' });
  expect(
    within(rail)
      .getAllByRole('link')
      .map((link) => link.textContent),
  ).toEqual([
    'L1Engineering4 people',
    'GLobby & Commons1 person',
    '1FInnovation LabQA & design studio',
  ]);
  expect(screen.getByTestId('scene')).toHaveTextContent('building');
});

it('goes back up from a floor to its building', async () => {
  serveCampus();
  renderApp('/office/floors/l1');

  expect(await screen.findByTestId('scene')).toHaveTextContent('floor');
  const crumbs = screen.getByRole('navigation', { name: 'Location' });
  expect(within(crumbs).getByText('L1 · Engineering')).toHaveAttribute('aria-current', 'page');

  await user.click(within(crumbs).getByRole('button', { name: 'Back' }));

  expect(await screen.findByTestId('scene')).toHaveTextContent('building');
});

it('tells people the floor does not exist', async () => {
  serveCampus();
  renderApp('/office/floors/nope');

  expect(await screen.findByText("This floor isn't open yet.")).toBeInTheDocument();
});

it('shows a loading state, then an error with a retry', async () => {
  serveCampus({ status: 500, code: 'INTERNAL_ERROR', detail: 'Something went wrong.' }, 500);
  renderApp('/office');

  expect(screen.getByText('Setting up the office…')).toBeInTheDocument();
  expect(await screen.findByText("We couldn't load the office.")).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
});

it('explains an empty campus', async () => {
  serveCampus({ buildings: [] });
  renderApp('/office');

  expect(await screen.findByText("Your office isn't set up yet.")).toBeInTheDocument();
});

it('explains when WebGL is off', async () => {
  vi.mocked(hasWebGL).mockReturnValue(false);
  serveCampus();
  renderApp('/office');

  expect(
    await screen.findByText('The 3D office needs WebGL, which this browser has turned off.'),
  ).toBeInTheDocument();
  expect(screen.queryByTestId('scene')).not.toBeInTheDocument();
});

it('walks you to your desk once you are on its floor', async () => {
  desk = { floorId: 'l1', x: 2, y: 1, kind: 'DESK' };
  serveCampus();
  renderApp('/office');

  await user.click(await screen.findByRole('button', { name: 'Go to my desk' }));

  expect(await screen.findByTestId('scene')).toHaveTextContent('floor');
  expect(walkTo).not.toHaveBeenCalled();
  const me = useSessionStore.getState().user!.id!;
  usePresenceStore
    .getState()
    .arrive([{ userId: me, displayName: 'Me', x: 1, y: 1, facing: 0, moving: false }]);
  await vi.waitFor(() => expect(walkTo).toHaveBeenCalledWith(2.5, 1.5));
});

it('explains how to get a desk when you have none', async () => {
  serveCampus();
  renderApp('/office');

  await user.click(await screen.findByRole('button', { name: 'Go to my desk' }));

  expect(
    screen.getByText("You don't have a desk yet. Sit at a free desk to make it yours."),
  ).toBeInTheDocument();
});

it('greets the first person on a floor and says when the connection drops', async () => {
  serveCampus();
  renderApp('/office/floors/l1');
  await screen.findByTestId('scene');
  const me = useSessionStore.getState().user!.id!;

  usePresenceStore.getState().setConnected(true);
  usePresenceStore
    .getState()
    .arrive([{ userId: me, displayName: 'Me', x: 1, y: 1, facing: 0, moving: false }]);
  expect(await screen.findByText("You're the first one on this floor.")).toBeInTheDocument();

  usePresenceStore.getState().setConnected(false);
  expect(await screen.findByText('Reconnecting…')).toBeInTheDocument();
});

it('explains focus mode, yours and someone else’s, and switches it from the toolbar', async () => {
  serveCampus();
  renderApp('/office/floors/l1');
  await screen.findByTestId('scene');
  const me = useSessionStore.getState().user!.id!;
  usePresenceStore.getState().setConnected(true);
  usePresenceStore.getState().arrive([
    { userId: me, displayName: 'Me', x: 1, y: 1, facing: 0, moving: false },
    { userId: 'nadia', displayName: 'Nadia Fahmy', x: 2, y: 1, facing: 0, moving: false },
  ]);
  expect(await screen.findByText('Walk close to a teammate to start talking.')).toBeInTheDocument();

  useConversationStore.getState().setFocused(['nadia']);
  expect(
    await screen.findByText("Nadia is in focus mode. They can't be interrupted right now."),
  ).toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: /^Focus mode/ }));
  expect(send).toHaveBeenCalledWith('/app/conversations/focus', { on: true });
  useConversationStore.getState().setFocused(['nadia', me]);
  expect(
    await screen.findByText('Focus mode on. Nobody can call or message you.'),
  ).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /^Focus mode/ })).toHaveAttribute(
    'aria-pressed',
    'true',
  );
});
