import {
  useCallback,
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type FormEvent,
} from 'react';
import { useConversationStore } from './conversationStore';
import type { Call } from './usePeerConnections';
import { useNames } from './useTalk';

// The office HUD keeps its daylight colours in both themes, so these panels do too.
const panel =
  'absolute rounded-[14px] border border-[rgba(20,27,45,.08)] bg-[rgba(255,255,255,.95)] text-[#141B2D] shadow-[0_10px_30px_rgba(20,27,45,.16)]';
const ctrl =
  'h-10 flex-1 cursor-pointer rounded-[9px] border border-[#D5DBE4] bg-white px-2 text-[13px] font-semibold text-[#141B2D] focus-visible:outline-2 focus-visible:outline-[#23395D]';
const ctrlOn = 'border-[#23395D] bg-[#23395D] text-white';
const ctrlLeave = 'border-[#8E2F25] bg-[#8E2F25] text-white';

const initials = (name: string) =>
  name
    .split(' ')
    .map((part) => part[0] ?? '')
    .join('')
    .slice(0, 2)
    .toUpperCase() || '?';

function Tile({
  name,
  stream,
  connected,
  me,
}: {
  name: string;
  stream?: MediaStream | null;
  connected: boolean;
  me?: boolean;
}) {
  const video = useRef<HTMLVideoElement>(null);
  const hasVideo = useLiveVideo(stream);
  useEffect(() => {
    if (video.current && stream) video.current.srcObject = stream;
  }, [stream, hasVideo]);
  return (
    <div
      role="group"
      aria-label={`${me ? 'You' : name}${connected ? ', connected' : ''}`}
      className={`relative grid h-24 place-items-center overflow-hidden rounded-[10px] bg-[#1B2A45] font-display text-[22px] font-semibold text-white ${connected ? 'shadow-[0_0_0_3px_#3FBF8A]' : ''}`}
    >
      {hasVideo ? (
        <video
          ref={video}
          autoPlay
          playsInline
          muted
          className="absolute inset-0 size-full object-cover"
        />
      ) : (
        initials(name)
      )}
      {!me && stream && <AudioOut stream={stream} />}
      <small className="absolute bottom-1.5 left-2 font-sans text-xs font-semibold">
        {me ? 'You' : name}
      </small>
    </div>
  );
}

// A remote video slot always exists; its track is muted until the other side turns a camera on.
function useLiveVideo(stream?: MediaStream | null) {
  const subscribe = useCallback(
    (changed: () => void) => {
      const tracks = stream?.getVideoTracks() ?? [];
      const events = ['mute', 'unmute', 'ended'];
      tracks.forEach((track) => events.forEach((e) => track.addEventListener(e, changed)));
      return () =>
        tracks.forEach((track) => events.forEach((e) => track.removeEventListener(e, changed)));
    },
    [stream],
  );
  return useSyncExternalStore(subscribe, () =>
    Boolean(stream?.getVideoTracks().some((t) => !t.muted && t.readyState === 'live')),
  );
}

function AudioOut({ stream }: { stream: MediaStream }) {
  const audio = useRef<HTMLAudioElement>(null);
  useEffect(() => {
    if (audio.current) audio.current.srcObject = stream;
  }, [stream]);
  return <audio ref={audio} autoPlay />;
}

export function CallPanel({ call, me }: { call: Call; me: string }) {
  const group = useConversationStore((state) => state.group);
  const names = useNames();
  if (!group) return null;
  const others = group.members.filter((id) => id !== me);
  if (group.route === 'TOO_BIG')
    return (
      <section
        aria-label="Call"
        className={`${panel} right-3 bottom-3 left-3 flex flex-col gap-2 p-2.5 sm:left-auto sm:w-[300px]`}
      >
        <p
          role="status"
          className="m-0 rounded-[9px] bg-[#F6EAD4] px-2.5 py-2 text-[13px] text-[#7A5216]"
        >
          Voice works for up to 4 people here. Use a meeting room for bigger groups.
        </p>
      </section>
    );
  return (
    <section
      aria-label="Call"
      className={`${panel} right-3 bottom-3 left-3 flex flex-col gap-2 p-2.5 sm:left-auto sm:w-[300px]`}
    >
      {call.micBlocked && (
        <p
          role="alert"
          className="m-0 rounded-[9px] bg-[#F6DFDB] px-2.5 py-2 text-[13px] text-[#8E2F25]"
        >
          We couldn't reach your microphone. Check your browser's permission and try again.
        </p>
      )}
      {call.inCall && (
        <div className="grid grid-cols-2 gap-2">
          {others.map((id) => (
            <Tile
              key={id}
              name={names[id] ?? ''}
              stream={call.streams[id]}
              connected={call.states[id] === 'connected'}
            />
          ))}
          <Tile name="You" stream={call.cameraOn ? call.local : null} connected={false} me />
        </div>
      )}
      <div className="flex gap-1.5">
        {call.micBlocked ? (
          <button type="button" className={`${ctrl} ${ctrlOn}`} onClick={call.retry}>
            Try again
          </button>
        ) : (
          <>
            <button
              type="button"
              className={ctrl}
              aria-pressed={call.muted}
              onClick={call.toggleMute}
            >
              {call.muted ? 'Unmute' : 'Mute'}
            </button>
            <button
              type="button"
              className={`${ctrl} ${call.cameraOn ? ctrlOn : ''}`}
              aria-pressed={call.cameraOn}
              onClick={call.toggleCamera}
            >
              {call.cameraOn ? 'Camera off' : 'Camera on'}
            </button>
          </>
        )}
        {call.inCall && (
          <button type="button" className={`${ctrl} ${ctrlLeave}`} onClick={call.leave}>
            Leave
          </button>
        )}
      </div>
    </section>
  );
}

export function ChatPanel({ me, onSend }: { me: string; onSend: (body: string) => void }) {
  const group = useConversationStore((state) => state.group);
  const messages = useConversationStore((state) => state.messages);
  const names = useNames();
  const [draft, setDraft] = useState('');
  if (!group) return null;
  const others = group.members.filter((id) => id !== me).map((id) => names[id] || 'someone');
  const title = others.length === 1 ? `${others[0]} and you` : `${others.length + 1} people`;
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const body = draft.trim();
    if (!body) return;
    onSend(body);
    setDraft('');
  };
  return (
    <section
      aria-label="Chat"
      className={`${panel} right-3 bottom-[150px] left-3 flex h-[220px] flex-col sm:top-[62px] sm:right-auto sm:bottom-auto sm:h-[300px] sm:w-[320px]`}
    >
      <h3 className="m-0 flex justify-between border-b border-[#E3E7EE] px-3.5 py-2.5 text-[13px] font-semibold">
        Chat · {title}
      </h3>
      <div
        aria-live="polite"
        className="flex flex-1 flex-col gap-2 overflow-auto px-3.5 py-2.5 text-[13.5px]"
      >
        {messages.map((m) =>
          m.senderId === me ? (
            <p
              key={m.id}
              className="m-0 max-w-[85%] self-end rounded-[10px_10px_2px_10px] bg-[#E4EAF4] px-2.5 py-1.5"
            >
              {m.body}
            </p>
          ) : (
            <p key={m.id} className="m-0">
              <b className="mr-1.5 font-semibold">{names[m.senderId] || 'Someone'}</b>
              <time className="text-xs text-[#5B6478]">
                {new Date(m.createdAt).toLocaleTimeString([], {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </time>
              <br />
              {m.body}
            </p>
          ),
        )}
      </div>
      <form onSubmit={submit} className="flex gap-1.5 border-t border-[#E3E7EE] p-2">
        <label htmlFor="chat-draft" className="sr-only">
          Message
        </label>
        <input
          id="chat-draft"
          value={draft}
          maxLength={2000}
          onChange={(event) => setDraft(event.target.value)}
          placeholder={others.length === 1 ? `Message ${others[0]}` : 'Message the group'}
          className="h-[38px] min-w-0 flex-1 rounded-[9px] border border-[#C9D0DB] bg-[#F7F8FA] px-3 text-[13.5px] text-[#141B2D]"
        />
        <button type="submit" className={`${ctrl} ${ctrlOn} flex-none px-3.5`}>
          Send
        </button>
      </form>
    </section>
  );
}
