import { useState, type FormEvent } from 'react';
import { Alert } from '../../shared/ui/Alert';
import { Sheet } from '../../shared/ui/Sheet';
import { useTeams } from '../admin-users/usePeople';
import { useSessionStore } from '../auth/sessionStore';
import { useBroadcast, useCallTownHall, useTownHall, type Sent } from './useAnnouncements';

const field =
  'w-full rounded-[10px] border border-field-line bg-field px-3.5 text-ink focus-visible:border-accent focus-visible:ring-2 focus-visible:ring-signal focus-visible:outline-none';
const label = 'mb-1.5 block text-[13px] font-semibold';
const primary =
  'h-11 cursor-pointer rounded-[10px] border-0 bg-accent px-[18px] font-semibold text-accent-ink disabled:opacity-70';
const secondary =
  'h-11 cursor-pointer rounded-[10px] border border-line bg-transparent px-[18px] font-semibold text-ink disabled:opacity-70';
const MAX = 1000;

const sentLine = (sent: Sent) =>
  `Sent to ${sent.audience} ${sent.audience === 1 ? 'person' : 'people'}. ${
    sent.online === sent.audience
      ? 'Everyone is online and sees it now.'
      : `${sent.online} online now; the rest see it when they open Corridor.`
  }`;

/** Broadcast to everyone or a team, and call or end the town hall (managers, HR and admins). */
export function BroadcastSheet({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Sheet
      open={open}
      onOpenChange={(next) => !next && onClose()}
      title="Broadcast"
      description="People in focus mode see it when they come out of focus."
    >
      {open && <BroadcastForm onDone={onClose} />}
    </Sheet>
  );
}

function BroadcastForm({ onDone }: { onDone: () => void }) {
  const role = useSessionStore((state) => state.user?.role);
  const teams = useTeams();
  const broadcast = useBroadcast();
  const townHall = useTownHall();
  const call = useCallTownHall();
  const [teamId, setTeamId] = useState('');
  const [body, setBody] = useState('');
  const [hallTitle, setHallTitle] = useState('All-hands');
  const [problem, setProblem] = useState<string | null>(null);
  const canPickTeam = role === 'HR' || role === 'ADMIN';

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!body.trim()) {
      setProblem('Write something to send.');
      return;
    }
    setProblem(null);
    broadcast.mutate(
      { teamId: teamId || null, body: body.trim() },
      { onSuccess: () => setBody('') },
    );
  };

  return (
    <form noValidate onSubmit={submit} className="flex min-h-0 flex-1 flex-col">
      <div className="flex flex-1 flex-col gap-[18px] overflow-auto px-7 py-5">
        {broadcast.data && !broadcast.isPending && (
          <Alert tone="info">{sentLine(broadcast.data)}</Alert>
        )}
        {broadcast.error && (
          <Alert tone="danger">{broadcast.error.detail ?? 'It didn’t send. Try again.'}</Alert>
        )}
        {canPickTeam && (
          <div>
            <label className={label} htmlFor="broadcast-to">
              To
            </label>
            <select
              id="broadcast-to"
              className={`${field} h-11`}
              value={teamId}
              onChange={(e) => setTeamId(e.target.value)}
            >
              <option value="">Everyone</option>
              {teams.data?.items.map((team) => (
                <option key={team.id} value={team.id}>
                  {team.name}
                </option>
              ))}
            </select>
          </div>
        )}
        <div>
          <label className={label} htmlFor="broadcast-body">
            Message
          </label>
          <textarea
            id="broadcast-body"
            rows={5}
            maxLength={MAX}
            value={body}
            aria-invalid={Boolean(problem)}
            aria-describedby="broadcast-count"
            onChange={(e) => setBody(e.target.value)}
            className={`${field} resize-y py-3`}
          />
          <p
            id="broadcast-count"
            className={`m-0 mt-1.5 text-[13px] ${problem ? 'text-danger-ink' : 'text-muted'}`}
          >
            {problem ?? `${body.length} / ${MAX}`}
          </p>
        </div>
        <section
          aria-labelledby="hall-title"
          className="flex flex-col gap-3 border-t border-line pt-5"
        >
          <h3 id="hall-title" className="m-0 font-display text-lg font-bold tracking-[-0.01em]">
            Town hall
          </h3>
          {call.error && (
            <Alert tone="danger">{call.error.detail ?? 'That didn’t work. Try again.'}</Alert>
          )}
          {townHall.data ? (
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="m-0 text-sm">
                <b>{townHall.data.title}</b> is on in the {townHall.data.roomName}.
              </p>
              <button
                type="button"
                className={secondary}
                disabled={call.isPending}
                onClick={() => call.mutate(null)}
              >
                End the town hall
              </button>
            </div>
          ) : (
            <div className="flex flex-wrap items-end gap-3">
              <div className="min-w-[180px] flex-1">
                <label className={label} htmlFor="hall-name">
                  Title
                </label>
                <input
                  id="hall-name"
                  className={`${field} h-11`}
                  value={hallTitle}
                  onChange={(e) => setHallTitle(e.target.value)}
                />
              </div>
              <button
                type="button"
                className={secondary}
                disabled={call.isPending}
                onClick={() => call.mutate({ title: hallTitle })}
              >
                Call a town hall
              </button>
            </div>
          )}
        </section>
      </div>
      <div className="flex justify-end gap-2.5 border-t border-line px-7 py-4">
        <button type="button" className={secondary} onClick={onDone}>
          Close
        </button>
        <button type="submit" className={primary} disabled={broadcast.isPending}>
          {broadcast.isPending ? 'Sending…' : 'Send'}
        </button>
      </div>
    </form>
  );
}
