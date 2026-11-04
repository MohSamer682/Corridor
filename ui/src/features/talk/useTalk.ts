import { useShallow } from 'zustand/react/shallow';
import { positionAt, usePresenceStore } from '../office/presence/presenceStore';
import { useConversationStore } from './conversationStore';

// Matches the server's open-floor hearing range.
const HEARING = 3;

/** First names of everyone on the floor, by user id. */
export function useNames() {
  return usePresenceStore(
    useShallow((state) =>
      Object.fromEntries(
        Object.values(state.people).map((p) => [p.id, p.name.split(' ')[0] ?? '']),
      ),
    ),
  );
}

/** What the bottom hint says about talking right now, or null. */
export function useTalkHint(me: string | null): string | null {
  const focused = useConversationStore((state) => state.focused);
  const inGroup = useConversationStore((state) => Boolean(state.group));
  const nearFocused = usePresenceStore((state) => {
    const mine = me ? state.people[me] : undefined;
    if (!mine || focused.includes(me ?? '')) return null;
    const here = positionAt(mine, Number.POSITIVE_INFINITY);
    const near = focused
      .map((id) => state.people[id])
      .find((p) => {
        if (!p) return false;
        const there = positionAt(p, Number.POSITIVE_INFINITY);
        return Math.max(Math.abs(there.x - here.x), Math.abs(there.y - here.y)) <= HEARING;
      });
    return near ? (near.name.split(' ')[0] ?? near.name) : null;
  });
  const others = usePresenceStore((state) => Object.keys(state.people).length > 1);
  if (me && focused.includes(me)) return 'Focus mode on. Nobody can call or message you.';
  if (nearFocused) return `${nearFocused} is in focus mode. They can't be interrupted right now.`;
  if (others && !inGroup) return 'Walk close to a teammate to start talking.';
  return null;
}
