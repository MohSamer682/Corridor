import { useState, type FormEvent } from 'react';
import { Alert } from '../../shared/ui/Alert';
import { Sheet } from '../../shared/ui/Sheet';
import { useSessionStore } from '../auth/sessionStore';
import { useInvitees, useMeetingRooms, useSchedule, type Meeting } from './useMeetings';

const field =
  'h-11 w-full rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none aria-[invalid=true]:border-danger-ink';
const label = 'mb-1.5 block text-[13px] font-semibold';
const error = 'm-0 mt-1.5 text-[13px] text-danger-ink';

const LENGTHS = [15, 30, 45, 60, 90, 120, 180, 240, 360, 480];
const lengthName = (minutes: number) =>
  minutes < 60
    ? `${minutes} min`
    : minutes % 60
      ? `${Math.floor(minutes / 60)} h ${minutes % 60} min`
      : `${minutes / 60} h`;

/** The next quarter hour, as the form's default start. */
function nextQuarter() {
  const start = new Date(Date.now() + 15 * 60_000);
  start.setMinutes(Math.ceil(start.getMinutes() / 15) * 15, 0, 0);
  const pad = (n: number) => String(n).padStart(2, '0');
  return {
    date: start.toLocaleDateString('en-CA'),
    time: `${pad(start.getHours())}:${pad(start.getMinutes())}`,
  };
}

export function ScheduleMeetingSheet({
  open,
  onClose,
  onStarted,
}: {
  open: boolean;
  onClose: () => void;
  /** A meeting booked to start now, to join straight away. */
  onStarted: (meeting: Meeting) => void;
}) {
  return (
    <Sheet
      open={open}
      onOpenChange={(next) => !next && onClose()}
      title="Schedule a meeting"
      description="Everyone you invite sees it on their Meetings page."
    >
      {open && (
        <ScheduleForm
          onDone={onClose}
          onStarted={(meeting) => {
            onClose();
            onStarted(meeting);
          }}
        />
      )}
    </Sheet>
  );
}

function ScheduleForm({
  onDone,
  onStarted,
}: {
  onDone: () => void;
  onStarted: (meeting: Meeting) => void;
}) {
  const rooms = useMeetingRooms();
  const people = useInvitees();
  const me = useSessionStore((state) => state.user?.id);
  const schedule = useSchedule();
  const [start] = useState(nextQuarter);
  const [title, setTitle] = useState('');
  const [date, setDate] = useState(start.date);
  const [time, setTime] = useState(start.time);
  const [minutes, setMinutes] = useState(30);
  const [roomId, setRoomId] = useState('');
  const [invitees, setInvitees] = useState<string[]>([]);
  const [problems, setProblems] = useState<Record<string, string>>({});

  const startsAt = new Date(`${date}T${time}`);
  // "Meet now" skips the date and start: the meeting begins as soon as it is booked.
  const book = (now: boolean) => {
    const found: Record<string, string> = {};
    if (!title.trim() || title.trim().length > 120) found.title = 'Use 1 to 120 characters.';
    if (!now && (Number.isNaN(startsAt.getTime()) || startsAt.getTime() <= Date.now()))
      found.time = 'Pick a time in the future.';
    if (!roomId) found.room = 'Pick a room.';
    setProblems(found);
    if (Object.keys(found).length) return;
    schedule.mutate(
      {
        roomId,
        title: title.trim(),
        ...(now ? {} : { startsAt: startsAt.toISOString() }),
        minutes,
        invitees,
      },
      { onSuccess: (meeting) => (now ? onStarted(meeting) : onDone()) },
    );
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    book(false);
  };

  const places = [...new Set(rooms.data?.map((r) => r.place) ?? [])];
  const clash = schedule.error?.status === 409 ? schedule.error.detail : null;
  return (
    <form noValidate onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
      <div className="flex flex-1 flex-col gap-[18px] overflow-auto px-7 py-5">
        {schedule.error && !clash && (
          <Alert tone="danger">{schedule.error.detail ?? 'Something went wrong. Try again.'}</Alert>
        )}
        <div>
          <label className={label} htmlFor="meeting-title">
            Title
          </label>
          <input
            id="meeting-title"
            className={field}
            maxLength={120}
            value={title}
            aria-invalid={Boolean(problems.title)}
            aria-describedby={problems.title ? 'meeting-title-error' : undefined}
            onChange={(e) => setTitle(e.target.value)}
          />
          {problems.title && (
            <p id="meeting-title-error" className={error}>
              {problems.title}
            </p>
          )}
        </div>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-[1.25fr_1fr_1.15fr]">
          <div>
            <label className={label} htmlFor="meeting-date">
              Date
            </label>
            <input
              id="meeting-date"
              type="date"
              className={field}
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </div>
          <div>
            <label className={label} htmlFor="meeting-start">
              Start
            </label>
            <input
              id="meeting-start"
              type="time"
              step={900}
              className={field}
              value={time}
              aria-invalid={Boolean(problems.time)}
              aria-describedby={problems.time ? 'meeting-time-error' : undefined}
              onChange={(e) => setTime(e.target.value)}
            />
          </div>
          <div>
            <label className={label} htmlFor="meeting-length">
              Length
            </label>
            <select
              id="meeting-length"
              className={field}
              value={minutes}
              onChange={(e) => setMinutes(Number(e.target.value))}
            >
              {LENGTHS.map((m) => (
                <option key={m} value={m}>
                  {lengthName(m)}
                </option>
              ))}
            </select>
          </div>
        </div>
        {problems.time && (
          <p id="meeting-time-error" className={`${error} -mt-3`}>
            {problems.time}
          </p>
        )}
        <div>
          <label className={label} htmlFor="meeting-room">
            Room
          </label>
          <select
            id="meeting-room"
            className={field}
            value={roomId}
            aria-invalid={Boolean(problems.room || clash)}
            aria-describedby={clash ? 'meeting-clash' : undefined}
            onChange={(e) => setRoomId(e.target.value)}
          >
            <option value="">Pick a room</option>
            {places.map((place) => (
              <optgroup key={place} label={place}>
                {rooms.data
                  ?.filter((r) => r.place === place)
                  .map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.name}
                    </option>
                  ))}
              </optgroup>
            ))}
          </select>
          {problems.room && <p className={error}>{problems.room}</p>}
          {clash && (
            <div id="meeting-clash" className="mt-2">
              <Alert tone="danger">{clash}</Alert>
            </div>
          )}
        </div>
        <fieldset className="m-0 border-0 p-0">
          <legend className={label}>Invite</legend>
          <div className="flex max-h-[180px] flex-col gap-1 overflow-auto rounded-[10px] border border-field-line bg-field p-2">
            {people.data
              ?.filter((p) => p.userId !== me)
              .map((p) => (
                <label
                  key={p.userId}
                  className="flex min-h-9 cursor-pointer items-center gap-2.5 px-1 text-[14px]"
                >
                  <input
                    type="checkbox"
                    checked={invitees.includes(p.userId)}
                    onChange={(e) =>
                      setInvitees((list) =>
                        e.target.checked
                          ? [...list, p.userId]
                          : list.filter((id) => id !== p.userId),
                      )
                    }
                  />
                  {p.displayName}
                  {p.teamName && <span className="text-muted">· {p.teamName}</span>}
                </label>
              ))}
          </div>
        </fieldset>
      </div>
      <div className="flex justify-end gap-2.5 border-t border-line px-7 py-4">
        <button
          type="button"
          className="h-11 cursor-pointer rounded-[10px] border border-line bg-transparent px-[18px] font-semibold text-ink"
          onClick={onDone}
        >
          Cancel
        </button>
        <button
          type="button"
          disabled={schedule.isPending}
          onClick={() => book(true)}
          className="h-11 cursor-pointer rounded-[10px] border border-accent bg-transparent px-[18px] font-semibold text-accent-text disabled:opacity-75"
        >
          Meet now
        </button>
        <button
          type="submit"
          disabled={schedule.isPending}
          className="h-11 cursor-pointer rounded-[10px] border-0 bg-accent px-[18px] font-semibold text-accent-ink disabled:opacity-75"
        >
          Schedule
        </button>
      </div>
    </form>
  );
}
