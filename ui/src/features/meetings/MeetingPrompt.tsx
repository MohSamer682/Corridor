import { Link } from 'react-router';
import { usePresenceStore } from '../office/presence/presenceStore';
import type { Room } from '../office/types';
import { canJoin, useMyMeetings } from './useMeetings';

/** Walking into a meeting room during one of your meetings offers to join it. */
export function MeetingPrompt({ me, rooms }: { me: string; rooms: Room[] }) {
  const meetings = useMyMeetings();
  const here = usePresenceStore((state) => {
    const mine = state.people[me];
    if (!mine) return null;
    const { x, y } = mine.to;
    return rooms.find((r) => x > r.minX && x < r.maxX && y > r.minY && y < r.maxY)?.id ?? null;
  });
  // Re-checked whenever you move or the list refreshes (every 30 s), which is often enough here.
  const meeting = here
    ? meetings.data?.find((m) => m.roomId === here && canJoin(m, meetings.dataUpdatedAt))
    : undefined;
  if (!meeting) return null;
  return (
    <div
      role="status"
      className="absolute bottom-[18px] left-1/2 flex max-w-[calc(100%-32px)] -translate-x-1/2 items-center gap-3 rounded-xl bg-[#1B2A45] py-2 pr-2 pl-4 font-semibold text-white shadow-[0_10px_30px_rgba(20,27,45,.35)]"
    >
      <span className="truncate">{meeting.title} is on here.</span>
      <Link
        to={`/meetings?join=${meeting.id}`}
        className="flex h-9 flex-none items-center rounded-[9px] bg-white px-3.5 text-sm text-[#141B2D] no-underline"
      >
        Join {meeting.title}
      </Link>
    </div>
  );
}
