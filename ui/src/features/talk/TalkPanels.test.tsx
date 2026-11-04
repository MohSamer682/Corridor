import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { usePresenceStore } from '../office/presence/presenceStore';
import { useConversationStore } from './conversationStore';
import { CallPanel, ChatPanel } from './TalkPanels';
import { isPolite, type Call } from './usePeerConnections';

const me = 'b-me';
const nour = 'a-nour';
const user = userEvent.setup();

const call = (overrides: Partial<Call> = {}): Call => ({
  streams: {},
  states: {},
  local: null,
  muted: false,
  cameraOn: false,
  micBlocked: false,
  inCall: true,
  toggleMute: vi.fn(),
  toggleCamera: vi.fn(),
  leave: vi.fn(),
  retry: vi.fn(),
  ...overrides,
});

beforeEach(() => {
  useConversationStore.getState().reset();
  usePresenceStore.getState().reset();
  usePresenceStore.getState().arrive([
    { userId: me, displayName: 'Mohamed Samer', x: 1, y: 1, facing: 'S', moving: false },
    { userId: nour, displayName: 'Nour Hassan', x: 2, y: 1, facing: 'S', moving: false },
  ]);
  useConversationStore
    .getState()
    .setGroup({ groupId: 'g1', members: [me, nour], route: 'PEER_TO_PEER' });
});

it('lets the lower user id make the first offer', () => {
  expect(isPolite(me, nour)).toBe(true);
  expect(isPolite(nour, me)).toBe(false);
});

it('starts a new chat when the group changes and ignores repeats', () => {
  const store = useConversationStore.getState();
  const message = { id: 'm1', senderId: nour, body: 'Hi', createdAt: '2026-11-04T10:42:00Z' };
  store.addMessage(message);
  store.addMessage(message);
  expect(useConversationStore.getState().messages).toHaveLength(1);

  store.setGroup({ groupId: 'g2', members: [me, nour, 'c'], route: 'PEER_TO_PEER' });

  expect(useConversationStore.getState().messages).toEqual([]);
});

it('labels the call controls and shows who is in the call', async () => {
  const controls = call();
  render(<CallPanel call={controls} me={me} />);

  expect(screen.getByText('Nour')).toBeInTheDocument();
  expect(screen.getByText('You')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Mute' }));
  expect(controls.toggleMute).toHaveBeenCalled();
  expect(screen.getByRole('button', { name: 'Camera on' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Leave' })).toBeInTheDocument();
});

it('explains a blocked microphone and offers to try again', async () => {
  const controls = call({ micBlocked: true });
  render(<CallPanel call={controls} me={me} />);

  expect(screen.getByRole('alert')).toHaveTextContent(
    "We couldn't reach your microphone. Check your browser's permission and try again.",
  );
  await user.click(screen.getByRole('button', { name: 'Try again' }));
  expect(controls.retry).toHaveBeenCalled();
});

it('keeps the same controls when a big group talks through the media server', () => {
  useConversationStore
    .getState()
    .setGroup({ groupId: 'g1', members: [me, nour, 'c', 'd', 'e'], route: 'SFU' });
  render(<CallPanel call={call()} me={me} />);

  expect(screen.getByRole('button', { name: 'Mute' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Leave' })).toBeInTheDocument();
});

it('sends chat and reads it out politely', async () => {
  const onSend = vi.fn();
  render(<ChatPanel me={me} onSend={onSend} />);
  useConversationStore.getState().addMessage({
    id: 'm1',
    senderId: nour,
    body: 'Got a minute?',
    createdAt: '2026-11-04T10:42:00Z',
  });

  expect(await screen.findByText('Got a minute?')).toBeInTheDocument();
  expect(screen.getByText('Got a minute?').closest('[aria-live]')).toHaveAttribute(
    'aria-live',
    'polite',
  );
  await user.type(
    screen.getByRole('textbox', { name: 'Message' }),
    'Sure, walking over now.{Enter}',
  );
  expect(onSend).toHaveBeenCalledWith('Sure, walking over now.');
  expect(screen.getByRole('heading', { name: 'Chat · Nour and you' })).toBeInTheDocument();
});
