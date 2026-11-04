import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router';
import { Alert } from '../../shared/ui/Alert';
import { useSessionStore } from '../auth/sessionStore';
import { Tile } from '../talk/TalkPanels';
import { useLiveKitCall } from '../talk/useLiveKitCall';
import { ScheduleMeetingSheet } from './ScheduleMeetingSheet';
import {
  canJoin,
  EARLY_MS,
  useCancelMeeting,
  useJoinMeeting,
  useMyMeetings,
  type Join,
  type Meeting,
} from './useMeetings';

const primary =
  'inline-flex h-9 cursor-pointer items-center rounded-[9px] border-0 bg-accent px-3.5 text-sm font-semibold text-accent-ink';
const secondary =
  'inline-flex h-9 cursor-pointer items-center rounded-[9px] border border-line bg-transparent px-3.5 text-sm font-semibold text-ink';
const chip =
  'inline-flex h-[26px] items-center gap-1.5 rounded-full px-2.5 text-[13px] font-semibold';
const timeFormat = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit' });
const dayFormat = new Intl.DateTimeFormat('en-GB', {
  weekday: 'short',
  day: 'numeric',
  month: 'short',
});

function length(meeting: Meeting) {
  const minutes = (Date.parse(meeting.endsAt) - Date.parse(meeting.startsAt)) / 60_000;
  return minutes % 60
    ? minutes > 60
      ? `${Math.floor(minutes / 60)} h ${minutes % 60} min`
      : `${minutes} min`
    : `${minutes / 60} h`;
}

/** Ticks once a minute so Join buttons and "Starts in" appear on their own. */
function useNow() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

export function MeetingsPage() {
  const meetings = useMyMeetings();
  const me = useSessionStore((state) => state.user?.id ?? '');
  const now = useNow();
  const [scheduling, setScheduling] = useState(false);
  const [params, setParams] = useSearchParams();
  const join = useJoinMeeting();
  const cancel = useCancelMeeting();
  const [inside, setInside] = useState<{ meeting: Meeting; ticket: Join } | null>(null);

  const open = (meeting: Meeting) =>
    join.mutate(meeting.id, { onSuccess: (ticket) => setInside({ meeting, ticket }) });

  // Walking into a meeting room in the office sends you here with ?join=<id>.
  const wanted = params.get('join');
  useEffect(() => {
    const meeting = meetings.data?.find((m) => m.id === wanted);
    if (!wanted || !meeting) return;
    setParams({}, { replace: true });
    join.mutate(meeting.id, { onSuccess: (ticket) => setInside({ meeting, ticket }) });
  }, [wanted, meetings.data, setParams, join]);

  if (inside)
    return (
      <MeetingStage
        meeting={inside.meeting}
        ticket={inside.ticket}
        onLeave={() => setInside(null)}
      />
    );

  const items = meetings.data ?? [];
  return (
    <div className="mx-auto box-border flex w-full max-w-[1120px] flex-col gap-5 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="m-0 font-display text-[30px] font-bold tracking-[-0.02em]">Meetings</h1>
          <p className="m-0 mt-1 text-muted">
            Today and the week ahead. Join opens 10 minutes before the start.
          </p>
        </div>
        <button
          type="button"
          className={`${primary} h-11 px-[18px] text-[15px]`}
          onClick={() => setScheduling(true)}
        >
          Schedule a meeting
        </button>
      </div>
      {join.error && (
        <Alert tone="danger">{join.error.detail ?? 'We couldn’t join. Try again.'}</Alert>
      )}
      {cancel.error && (
        <Alert tone="danger">{cancel.error.detail ?? 'We couldn’t cancel. Try again.'}</Alert>
      )}
      {meetings.isError && <Alert tone="danger">We couldn’t load your meetings. Try again.</Alert>}
      {meetings.data && items.length === 0 && (
        <div className="flex flex-col items-center gap-2.5 rounded-[14px] border border-line bg-surface px-6 py-14 text-center">
          <h2 className="m-0 font-display text-xl">No meetings today.</h2>
          <p className="m-0 text-muted">Book a room for your team.</p>
        </div>
      )}
      {items.length > 0 && (
        <ul className="m-0 list-none rounded-[14px] border border-line bg-surface p-0">
          {items.map((meeting) => {
            const starts = Date.parse(meeting.startsAt);
            const soon = meeting.status === 'SCHEDULED' && starts > now && starts - now <= EARLY_MS;
            return (
              <li
                key={meeting.id}
                className="flex flex-wrap items-center gap-3.5 border-b border-line px-[18px] py-3.5 last:border-b-0"
              >
                <time
                  dateTime={meeting.startsAt}
                  className="w-[110px] flex-none font-semibold tabular-nums"
                >
                  {timeFormat.format(starts)}
                  <small className="block font-normal text-muted">
                    {dayFormat.format(starts)} · {length(meeting)}
                  </small>
                </time>
                <div className="min-w-0 flex-1">
                  <b
                    className={`block ${meeting.status === 'CANCELLED' ? 'text-muted line-through' : ''}`}
                  >
                    {meeting.title}
                  </b>
                  <span className="text-sm text-muted">
                    {meeting.roomName} · {meeting.place} · {meeting.attendees.length + 1} people
                  </span>
                </div>
                {meeting.status === 'LIVE' && (
                  <span className={`${chip} bg-info-bg text-info-ink`}>
                    <span className="size-1.5 rounded-full bg-[#3FBF8A]" aria-hidden="true" />
                    Live now
                  </span>
                )}
                {soon && (
                  <span className={`${chip} bg-info-bg text-info-ink`}>
                    Starts in {Math.ceil((starts - now) / 60_000)} min
                  </span>
                )}
                {meeting.status === 'CANCELLED' && (
                  <span className={`${chip} border border-line bg-field text-muted`}>
                    Cancelled
                  </span>
                )}
                {canJoin(meeting, now) && (
                  <button
                    type="button"
                    className={primary}
                    disabled={join.isPending}
                    onClick={() => open(meeting)}
                  >
                    Join {meeting.title}
                  </button>
                )}
                {meeting.organizerId === me && meeting.status === 'SCHEDULED' && (
                  <button
                    type="button"
                    className={secondary}
                    aria-label={`Cancel meeting ${meeting.title}`}
                    onClick={() => cancel.mutate(meeting.id)}
                  >
                    Cancel meeting
                  </button>
                )}
              </li>
            );
          })}
        </ul>
      )}
      <ScheduleMeetingSheet open={scheduling} onClose={() => setScheduling(false)} />
    </div>
  );
}

function MeetingStage({
  meeting,
  ticket,
  onLeave,
}: {
  meeting: Meeting;
  ticket: Join;
  onLeave: () => void;
}) {
  const call = useLiveKitCall(ticket);
  const people = Object.keys(call.names);
  const leave = () => {
    call.leave();
    onLeave();
  };
  return (
    <div className="mx-auto box-border flex w-full max-w-[1240px] flex-col gap-4 px-4 py-6 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="m-0 font-display text-2xl font-bold tracking-[-0.02em]">
            {meeting.title}
          </h1>
          <p className="m-0 mt-1 text-muted">
            {meeting.roomName} · {timeFormat.format(Date.parse(meeting.startsAt))} –{' '}
            {timeFormat.format(Date.parse(meeting.endsAt))} · {people.length + 1} people
          </p>
        </div>
      </div>
      {call.micBlocked && (
        <Alert tone="danger">
          We couldn't reach your microphone. Check your browser's permission and try again.
        </Alert>
      )}
      <section
        aria-label="Meeting"
        className="grid grid-cols-2 gap-2 rounded-2xl bg-[#0F1726] p-2 sm:grid-cols-3"
      >
        {people.map((id) => (
          <Tile
            key={id}
            name={call.names[id] ?? ''}
            stream={call.streams[id]}
            connected={call.states[id] === 'connected'}
          />
        ))}
        <Tile name="You" stream={call.local} connected={false} me />
      </section>
      <div className="flex justify-center gap-2">
        <button
          type="button"
          className={secondary}
          aria-pressed={call.muted}
          onClick={call.toggleMute}
        >
          {call.muted ? 'Unmute' : 'Mute'}
        </button>
        <button
          type="button"
          className={secondary}
          aria-pressed={call.cameraOn}
          onClick={call.toggleCamera}
        >
          {call.cameraOn ? 'Camera off' : 'Camera on'}
        </button>
        <button
          type="button"
          className={`${secondary} border-danger-ink bg-danger-ink text-surface`}
          onClick={leave}
        >
          Leave
        </button>
      </div>
    </div>
  );
}
