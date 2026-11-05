import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router';
import { useSessionStore } from '../auth/sessionStore';
import { useConversationStore } from '../talk/conversationStore';
import { useDismiss, useTownHall, useUnread } from './useAnnouncements';

const timeFormat = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });

function Megaphone() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1zM15 8a5 5 0 0 1 0 8M18 5a9 9 0 0 1 0 14" />
    </svg>
  );
}

const bar =
  'mx-auto flex w-full max-w-[1120px] items-start gap-3 px-4 py-3 sm:items-center sm:px-6';
const action =
  'inline-flex h-9 flex-none cursor-pointer items-center rounded-[9px] px-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-signal';

/**
 * Under the header on every page: the live town hall with a way in, then the oldest unread announcement. Nothing
 * shows while you are in focus mode; it waits until you come out of it.
 */
export function AnnouncementBanner() {
  const me = useSessionStore((state) => state.user?.id ?? '');
  const inFocus = useConversationStore((state) => state.focused.includes(me));
  const unread = useUnread();
  const townHall = useTownHall();
  const dismiss = useDismiss();
  const navigate = useNavigate();
  const [skippedHall, setSkippedHall] = useState<string | null>(null);
  const refetchUnread = unread.refetch;
  const refetchHall = townHall.refetch;
  useEffect(() => {
    const nudge = () => {
      void refetchUnread();
      void refetchHall();
    };
    window.addEventListener('corridor:announcement', nudge);
    return () => window.removeEventListener('corridor:announcement', nudge);
  }, [refetchUnread, refetchHall]);
  if (inFocus) return null;

  const hall = townHall.data;
  if (hall && skippedHall !== hall.startedAt) {
    return (
      <section aria-label="Town hall" className="border-b border-line bg-brand-bg text-brand-ink">
        <div className={bar} role="status">
          <span className="mt-0.5 flex-none text-signal sm:mt-0">
            <Megaphone />
          </span>
          <p className="m-0 flex-1 text-sm">
            <b className="font-semibold">{hall.title}</b> is on now in the {hall.roomName}. Walk
            over to join.
          </p>
          <button
            type="button"
            className={`${action} border-0 bg-signal text-[#1A1306]`}
            onClick={() =>
              navigate(`/office/floors/${hall.floorId}`, {
                state: { walkTo: { x: Math.floor(hall.x), y: Math.floor(hall.y) } },
              })
            }
          >
            Join the town hall
          </button>
          <button
            type="button"
            className={`${action} border-0 bg-transparent text-brand-muted`}
            onClick={() => setSkippedHall(hall.startedAt)}
          >
            Not now
          </button>
        </div>
      </section>
    );
  }

  const next = unread.data?.[0];
  if (!next) return null;
  const more = (unread.data?.length ?? 1) - 1;
  return (
    <section aria-label="Announcement" className="border-b border-line bg-surface">
      <div className={bar} role="status">
        <span className="mt-0.5 flex-none text-signal sm:mt-0">
          <Megaphone />
        </span>
        <div className="min-w-0 flex-1 text-sm">
          <p className="m-0 text-ink">{next.body}</p>
          <p className="m-0 text-muted">
            {next.authorName} · {next.teamName ? `to ${next.teamName}` : 'to everyone'} ·{' '}
            {timeFormat.format(new Date(next.createdAt))}
            {more > 0 && ` · ${more} more`}
          </p>
        </div>
        <button
          type="button"
          className={`${action} border border-line bg-surface text-ink hover:bg-field`}
          onClick={() => dismiss.mutate(next.id)}
        >
          {more > 0 ? 'Next' : 'Dismiss'}
        </button>
      </div>
    </section>
  );
}
